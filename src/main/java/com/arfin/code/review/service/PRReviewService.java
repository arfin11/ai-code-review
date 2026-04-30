package com.arfin.code.review.service;

import com.arfin.code.review.github.GitHubService;
import com.arfin.code.review.model.DiffLine;
import com.arfin.code.review.model.FileDiff;
import com.arfin.code.review.model.ReviewComment;
import com.arfin.code.review.model.ReviewResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PRReviewService {

    private final GitHubService gitHubService;
    private final DiffParserService parser;
    private final CommentMapperService mapper;
    private final LLMService llm;

    // ✅ Limits
    private static final int MAX_FILES = 5;
    private static final int MAX_TOTAL_PATCH_CHARS = 20000;

    public PRReviewService(GitHubService gitHubService,
                           DiffParserService parser,
                           CommentMapperService mapper,
                           LLMService llm) {
        this.gitHubService = gitHubService;
        this.parser = parser;
        this.mapper = mapper;
        this.llm = llm;
    }

    public void reviewPR(String repo, int pr, int installationId) {

        try {

            List<FileDiff> files = gitHubService.getPRFiles(repo, pr, installationId);
            String sha = gitHubService.getLatestSha(repo, pr, installationId);

            // ✅ Calculate total patch size
            int totalSize = files.stream()
                    .filter(f -> f.getPatch() != null)
                    .mapToInt(f -> f.getPatch().length())
                    .sum();

            boolean exceedsFiles = files.size() > MAX_FILES;
            boolean exceedsSize = totalSize > MAX_TOTAL_PATCH_CHARS;

            // 🚫 BLOCK if limits exceeded
            if (exceedsFiles || exceedsSize) {

                List<ReviewComment> blockedComments = new ArrayList<>();

                StringBuilder reason = new StringBuilder();
                reason.append("❌ AI Code Review Blocked\n\n");

                if (exceedsFiles) {
                    reason.append("- Too many files: ")
                            .append(files.size())
                            .append(" (max ").append(MAX_FILES).append(")\n");
                }

                if (exceedsSize) {
                    reason.append("- Diff too large: ")
                            .append(totalSize)
                            .append(" chars (max ")
                            .append(MAX_TOTAL_PATCH_CHARS)
                            .append(")\n");
                }

                reason.append("\nPlease reduce PR size and push again.");

                ReviewComment rc = new ReviewComment();
                rc.setSeverity("ERROR");
                rc.setIssue(reason.toString());
                rc.setSuggestion("Split PR into smaller chunks");
                rc.setFileName("N/A");
                rc.setLineNumber(0);

                blockedComments.add(rc);

                // ✅ FAIL check (blocks merge)
                gitHubService.createCheckRun(
                        repo,
                        sha,
                        installationId,
                        false,
                        blockedComments
                );

                return;
            }

            List<Map<String, Object>> allComments = new ArrayList<>();
            List<ReviewComment> allCommentsRaw = new ArrayList<>();

            boolean hasError = false;

            for (FileDiff file : files) {

                if (file.getPatch() == null) continue;

                String patch = file.getPatch();

                // ✅ Limit patch size for LLM
                if (patch.length() > 4000) {
                    patch = patch.substring(0, 4000);
                }

                ReviewResponse response =
                        llm.review(patch, file.getFilename());

                List<DiffLine> addedLines =
                        parser.extractAddedLines(file.getPatch());

                for (ReviewComment c : response.getComments()) {

                    c.setFileName(file.getFilename());

                    allCommentsRaw.add(c);

                    if ("ERROR".equalsIgnoreCase(c.getSeverity())) {
                        hasError = true;
                    }

                    DiffLine match =
                            mapper.mapToLine(addedLines, c.getLineHint());

                    if (match == null) continue;

                    c.setLineNumber(match.getLineNumber());

                    allComments.add(Map.of(
                            "path", file.getFilename(),
                            "line", match.getLineNumber(),
                            "side", "RIGHT",
                            "body", "❌ **" + c.getSeverity() + "**\n"
                                    + c.getIssue() + "\n\n💡 "
                                    + c.getSuggestion()
                    ));
                }
            }

            // ✅ Inline comments
            if (!allComments.isEmpty()) {
                gitHubService.postReview(repo, pr, sha, installationId, allComments);
            }

            // ✅ FINAL CHECK RUN
            gitHubService.createCheckRun(
                    repo,
                    sha,
                    installationId,
                    !hasError,
                    allCommentsRaw
            );

        } catch (Exception e) {
            e.printStackTrace();

            try {
                List<ReviewComment> errorComments = new ArrayList<>();

                ReviewComment rc = new ReviewComment();
                rc.setSeverity("ERROR");
                rc.setIssue("Internal error during AI review");
                rc.setSuggestion("Retry or check logs");
                rc.setFileName("N/A");
                rc.setLineNumber(0);

                errorComments.add(rc);

                gitHubService.createCheckRun(
                        repo,
                        gitHubService.getLatestSha(repo, pr, installationId),
                        installationId,
                        false,
                        errorComments
                );

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}