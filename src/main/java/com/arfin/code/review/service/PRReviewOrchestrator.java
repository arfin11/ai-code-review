package com.arfin.code.review.service;

import com.arfin.code.review.controller.GitHubWebhookController;
import com.arfin.code.review.model.FileDiff;
import com.arfin.code.review.model.ReviewComment;
import com.arfin.code.review.model.ReviewFinding;
import com.arfin.code.review.model.ReviewContext;
import com.arfin.code.review.model.ReviewResult;
import com.arfin.code.review.model.ReviewResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PRReviewOrchestrator {

    private final FileContextService fileContextService;
    private final FileContextTool fileContextTool;
    private final ParallelReviewWorkflow parallelReviewWorkflow;
    private final ReviewAggregator reviewAggregator;
    private final ReviewVerifier reviewVerifier;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("\"anchor\"\\s*:\\s*(.+?)(\\s*,\\s*\"source\")", Pattern.DOTALL);

    public PRReviewOrchestrator(FileContextService fileContextService,
                               FileContextTool fileContextTool,
                               ParallelReviewWorkflow parallelReviewWorkflow,
                               ReviewAggregator reviewAggregator,
                               ReviewVerifier reviewVerifier) {
        this.fileContextService = fileContextService;
        this.fileContextTool = fileContextTool;
        this.parallelReviewWorkflow = parallelReviewWorkflow;
        this.reviewAggregator = reviewAggregator;
        this.reviewVerifier = reviewVerifier;
    }

    public List<ReviewComment> review(String repo, int prNumber, List<FileDiff> files, int installationId) throws Exception {
        MDC.put("repo", repo);
        MDC.put("prNumber", String.valueOf(prNumber));
        MDC.put("installationId", String.valueOf(installationId));

        try {
            List<ReviewContext> contexts = fileContextService.buildContexts(files);
            log.info("Built {} review contexts for PR {}", contexts == null ? 0 : contexts.size(), prNumber);

            String reviewInput = buildReviewInput(contexts);
            log.info("Submitting review payload to parallel workflow. fileCount={}, snippetSize={}",
                    contexts == null ? 0 : contexts.size(), reviewInput.length());

            ReviewResult reviewResult;
            try (AutoCloseable ignored = fileContextTool.openReviewSession(repo, installationId, prNumber)) {
                ResultWithAgenticScope workflowResult = parallelReviewWorkflow.run(reviewInput);
                reviewResult = extractReviewResult(workflowResult);
            }

            List<ReviewFinding> merged = reviewAggregator.aggregate(
                    findingsOf(reviewResult.getGeneralFindings()),
                    findingsOf(reviewResult.getSecurityFindings())
            );
            log.info("Aggregated {} findings before verification", merged == null ? 0 : merged.size());

            List<ReviewFinding> verified = reviewVerifier.verify(merged);
            log.info("Verified {} findings after filtering", verified == null ? 0 : verified.size());

            List<ReviewComment> comments = new ArrayList<>();
            for (ReviewFinding finding : verified) {
                ReviewComment comment = new ReviewComment();
                comment.setFileName(finding.getFileName());
                comment.setLineNumber(finding.getLineNumber());
                comment.setSeverity(finding.getSeverity());
                comment.setIssue(finding.getIssue());
                comment.setSuggestion(finding.getSuggestion());
                comment.setAnchor(finding.getAnchor());
                comments.add(comment);
            }

            log.info("Prepared {} review comments for GitHub", comments.size());
            return comments;
        } finally {
            MDC.remove("repo");
            MDC.remove("prNumber");
            MDC.remove("installationId");
        }
    }

    private String buildReviewInput(List<ReviewContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "No code changes to review.";
        }

        StringBuilder sb = new StringBuilder();
        for (ReviewContext context : contexts) {
            sb.append("FILE: ").append(context.getFileName()).append("\n");
            if (context.getSnippet() != null && !context.getSnippet().isBlank()) {
                sb.append(context.getSnippet()).append("\n\n");
            }
        }
        return sb.toString();
    }

    private List<ReviewFinding> findingsOf(ReviewResponse response) {
        if (response == null || response.getComments() == null) {
            return Collections.emptyList();
        }
        return response.getComments();
    }

    private ReviewResult extractReviewResult(ResultWithAgenticScope workflowResult) {
        if (workflowResult == null || workflowResult.agenticScope() == null) {
            throw new IllegalStateException("Parallel review workflow returned no agentic scope");
        }

        AgenticScope scope = workflowResult.agenticScope();
        return new ReviewResult(
                readReviewResponse(scope, "generalFindings"),
                readReviewResponse(scope, "securityFindings")
        );
    }

    private ReviewResponse readReviewResponse(AgenticScope scope, String key) {
        Object state = scope.readState(key);
        if (state == null) {
            return null;
        }
        if (state instanceof ReviewResponse reviewResponse) {
            return reviewResponse;
        }
        if (state instanceof String rawResponse) {
            return parseReviewResponse(rawResponse, key);
        }
        throw new IllegalStateException("Unexpected workflow state for key '" + key + "': " + state.getClass().getName());
    }

    private ReviewResponse parseReviewResponse(String rawResponse, String key) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        String json = extractJsonObject(rawResponse);
        String repairedJson = repairAnchorFields(json);

        try {
            return objectMapper.readValue(repairedJson, ReviewResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse workflow response for key '" + key + "': " + repairedJson, e);
        }
    }

    private String extractJsonObject(String rawResponse) {
        int start = rawResponse.indexOf('{');
        int end = rawResponse.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("LLM response did not contain a JSON object: " + rawResponse);
        }
        return rawResponse.substring(start, end + 1);
    }

    private String repairAnchorFields(String json) {
        Matcher matcher = ANCHOR_PATTERN.matcher(json);
        StringBuffer repaired = new StringBuffer();
        while (matcher.find()) {
            String rawAnchor = matcher.group(1).trim();
            String normalizedAnchor = normalizeAnchorValue(rawAnchor);
            String replacement = "\"anchor\": " + toJsonString(normalizedAnchor) + matcher.group(2);
            matcher.appendReplacement(repaired, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(repaired);
        return repaired.toString();
    }

    private String normalizeAnchorValue(String rawAnchor) {
        try {
            return objectMapper.readValue(rawAnchor, String.class);
        } catch (Exception ignored) {
            String value = rawAnchor;
            if (value.endsWith(",")) {
                value = value.substring(0, value.length() - 1);
            }
            return value.trim();
        }
    }

    private String toJsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode anchor as JSON string", e);
        }
    }
}
