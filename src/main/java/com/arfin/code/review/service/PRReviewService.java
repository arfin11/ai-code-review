package com.arfin.code.review.service;

import com.arfin.code.review.github.GitHubService;
import com.arfin.code.review.model.FileDiff;
import com.arfin.code.review.model.ReviewComment;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PRReviewService {

    private static final int MAX_FILES_PER_PR = ReviewBudgetPolicy.MAX_FILES_PER_PR;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".java");

    private final GitHubService gitHubService;
    private final PRReviewOrchestrator prReviewOrchestrator;

    public PRReviewService(GitHubService gitHubService, PRReviewOrchestrator prReviewOrchestrator) {
        this.gitHubService = gitHubService;
        this.prReviewOrchestrator = prReviewOrchestrator;
    }

    public void reviewPR(String repo, int pr, int installationId) {
        MDC.put("repo", repo);
        MDC.put("prNumber", String.valueOf(pr));
        MDC.put("installationId", String.valueOf(installationId));
        log.info("Starting PR review workflow");

        try {
            List<FileDiff> files = gitHubService.getPRFiles(repo, pr, installationId);
            log.info("Fetched {} PR files from GitHub", files == null ? 0 : files.size());

            List<FileDiff> limitedFiles = files.stream()
                    .filter(file -> file != null && file.getFilename() != null)
                    .filter(file -> shouldReview(file.getFilename()))
                    .limit(MAX_FILES_PER_PR)
                    .collect(Collectors.toList());
            log.info("Filtered PR files to {} reviewable Java files", limitedFiles.size());

            List<ReviewComment> comments = prReviewOrchestrator.review(repo, pr, limitedFiles, installationId);
            log.info("Review workflow produced {} review comments", comments == null ? 0 : comments.size());

            publishResults(repo, pr, installationId, comments);
            log.info("PR review workflow completed successfully");
        } catch (Exception e) {
            log.error("PR review workflow failed", e);
            throw new RuntimeException("PR Review failed", e);
        } finally {
            MDC.remove("repo");
            MDC.remove("prNumber");
            MDC.remove("installationId");
        }
    }

    private boolean shouldReview(String filename) {
        return ALLOWED_EXTENSIONS.stream().anyMatch(filename::endsWith);
    }

    private void publishResults(String repo, int pr, int installationId, List<ReviewComment> comments) throws Exception {
        String sha = gitHubService.getLatestSha(repo, pr, installationId);
        boolean hasError = comments.stream()
                .anyMatch(c -> "ERROR".equalsIgnoreCase(c.getSeverity()));

        log.info("Publishing review results for sha={}, comments={}, hasError={}", sha, comments == null ? 0 : comments.size(), hasError);
        gitHubService.setCommitStatus(repo, sha, installationId, !hasError);
        gitHubService.createCheckRun(repo, sha, installationId, !hasError, comments);
    }
}
