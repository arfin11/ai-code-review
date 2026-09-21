package com.arfin.code.review.service;

import com.arfin.code.review.model.FileDiff;
import com.arfin.code.review.model.ReviewComment;
import com.arfin.code.review.model.ReviewContext;
import com.arfin.code.review.model.ReviewFinding;
import com.arfin.code.review.model.ReviewResponse;
import com.arfin.code.review.model.ReviewResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PRReviewOrchestrator {

    private final FileContextService fileContextService;
    private final FileContextTool fileContextTool;
    private final GeneralReviewAI generalReviewAI;
    private final SecurityReviewAI securityReviewAI;
    private final ReviewAggregator reviewAggregator;
    private final ReviewVerifier reviewVerifier;
    private final ReviewPromptService reviewPromptService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TRACE_ID_KEY = "traceId";
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("\\\"anchor\\\"\\s*:\\s*(.+?)(\\s*,\\s*\\\"source\\\")", Pattern.DOTALL);

    public PRReviewOrchestrator(FileContextService fileContextService,
                                FileContextTool fileContextTool,
                                GeneralReviewAI generalReviewAI,
                                SecurityReviewAI securityReviewAI,
                                ReviewAggregator reviewAggregator,
                                ReviewVerifier reviewVerifier,
                                ReviewPromptService reviewPromptService) {
        this.fileContextService = fileContextService;
        this.fileContextTool = fileContextTool;
        this.generalReviewAI = generalReviewAI;
        this.securityReviewAI = securityReviewAI;
        this.reviewAggregator = reviewAggregator;
        this.reviewVerifier = reviewVerifier;
        this.reviewPromptService = reviewPromptService;
    }

    public List<ReviewComment> review(String repo, int prNumber, List<FileDiff> files, int installationId) throws Exception {
        MDC.put("repo", repo);
        MDC.put("prNumber", String.valueOf(prNumber));
        MDC.put("installationId", String.valueOf(installationId));

        try {
            List<ReviewContext> contexts = fileContextService.buildContexts(files);
            log.info("Built {} review contexts for PR {}", contexts == null ? 0 : contexts.size(), prNumber);

            String reviewInput = buildReviewInput(contexts);
            String language = detectLanguage(contexts);
            String generalPrompt = reviewPromptService.generalPrompt(language);
            String securityPrompt = reviewPromptService.securityPrompt(language);
            log.info("Submitting review payload to review agents. fileCount={}, snippetSize={}, language={}",
                    contexts == null ? 0 : contexts.size(), reviewInput.length(), language);

            ReviewResult reviewResult;
            try (AutoCloseable ignored = fileContextTool.openReviewSession(repo, installationId, prNumber)) {
                reviewResult = runReviewsWithCurrentMdc(language, generalPrompt, securityPrompt, reviewInput);
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

    private String detectLanguage(List<ReviewContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "java";
        }
        for (ReviewContext context : contexts) {
            if (context != null && context.getFileName() != null && context.getFileName().endsWith(".java")) {
                return "java";
            }
        }
        return "java";
    }

    private ReviewResult runReviewsWithCurrentMdc(String language,
                                                  String generalPrompt,
                                                  String securityPrompt,
                                                  String reviewInput) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        if (mdcContext == null || mdcContext.isEmpty()) {
            return runReviews(language, generalPrompt, securityPrompt, reviewInput);
        }

        Map<String, String> contextToRestore = new HashMap<>(mdcContext);
        if (!contextToRestore.containsKey(TRACE_ID_KEY)) {
            return runReviews(language, generalPrompt, securityPrompt, reviewInput);
        }

        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        try {
            MDC.setContextMap(contextToRestore);
            return runReviews(language, generalPrompt, securityPrompt, reviewInput);
        } finally {
            if (previousContext == null || previousContext.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousContext);
            }
        }
    }

    private ReviewResult runReviews(String language,
                                    String generalPrompt,
                                    String securityPrompt,
                                    String reviewInput) {
        CompletableFuture<ReviewResponse> generalFuture = CompletableFuture.supplyAsync(
                () -> invokeReview(generalReviewAI, language, generalPrompt, reviewInput, "general")
        );
        CompletableFuture<ReviewResponse> securityFuture = CompletableFuture.supplyAsync(
                () -> invokeReview(securityReviewAI, language, securityPrompt, reviewInput, "security")
        );

        try {
            return new ReviewResult(generalFuture.join(), securityFuture.join());
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Review agent execution failed", cause);
        }
    }

    private List<ReviewFinding> findingsOf(ReviewResponse response) {
        if (response == null || response.getComments() == null) {
            return Collections.emptyList();
        }
        return response.getComments();
    }

    private ReviewResponse invokeReview(ReviewAgent agent,
                                        String language,
                                        String systemPrompt,
                                        String reviewInput,
                                        String agentName) {
        String rawResponse = agent.review(language, systemPrompt, reviewInput);
        return parseReviewResponse(rawResponse, agentName);
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
