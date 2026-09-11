package com.arfin.code.review.service;

import com.arfin.code.review.github.GitHubService;
import com.arfin.code.review.model.FileDiff;
import com.arfin.code.review.model.ReviewComment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
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
        try {
            List<FileDiff> files = gitHubService.getPRFiles(repo, pr, installationId);
            List<FileDiff> limitedFiles = files.stream()
                    .filter(file -> file != null && file.getFilename() != null)
                    .filter(file -> shouldReview(file.getFilename()))
                    .limit(MAX_FILES_PER_PR)
                    .collect(Collectors.toList());

            List<ReviewComment> comments = prReviewOrchestrator.review(repo, pr, limitedFiles, installationId);
            publishResults(repo, pr, installationId, comments);
        } catch (Exception e) {
            throw new RuntimeException("PR Review failed", e);
        }
    }

    private boolean shouldReview(String filename) {
        return ALLOWED_EXTENSIONS.stream().anyMatch(filename::endsWith);
    }

    private void publishResults(String repo, int pr, int installationId, List<ReviewComment> comments) throws Exception {
        String sha = gitHubService.getLatestSha(repo, pr, installationId);
        boolean hasError = comments.stream()
                .anyMatch(c -> "ERROR".equalsIgnoreCase(c.getSeverity()));

        gitHubService.setCommitStatus(repo, sha, installationId, !hasError);
        gitHubService.createCheckRun(repo, sha, installationId, !hasError, comments);
    }
}
