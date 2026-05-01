package com.arfin.code.review.service;

import com.arfin.code.review.github.GitHubService;
import com.arfin.code.review.model.FileDiff;
import com.arfin.code.review.model.ReviewComment;
import com.arfin.code.review.model.ReviewResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PRReviewService {

    private final GitHubService gitHubService;
    private final LLMService llmService;

    private static final int MAX_BATCH_SIZE = 4000;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".java", ".kt", ".js");

    public PRReviewService(GitHubService gitHubService,
                           LLMService llmService) {
        this.gitHubService = gitHubService;
        this.llmService = llmService;
    }

    public void reviewPR(String repo, int pr, int installationId) {

        try {
            List<FileDiff> files = gitHubService.getPRFiles(repo, pr, installationId);
            List<ReviewComment> allComments = new ArrayList<>();

            for (FileDiff file : files) {

                if (!shouldReview(file.getFilename())) continue;

                String patch = file.getPatch();
                if (patch == null || patch.isBlank()) continue;

                List<IndexedLine> indexedLines = extractIndexedLines(patch);
                if (indexedLines.isEmpty()) continue;

                // ✅ Rule-based detection (unreachable code)
                allComments.addAll(detectUnreachableCode(indexedLines, file.getFilename()));

                List<List<IndexedLine>> chunks = chunkByLines(indexedLines);
                List<List<IndexedLine>> batchedChunks = batchChunks(chunks);

                for (List<IndexedLine> batch : batchedChunks) {

                    String input = buildIndexedInput(batch);

                    ReviewResponse response =
                            llmService.review(input, file.getFilename());

                    if (response == null || response.getComments() == null) continue;

                    for (ReviewComment c : response.getComments()) {

                        c.setFileName(file.getFilename());

                        int line = mapAnchorToLine(c.getAnchor(), batch);
                        c.setLineNumber(line);

                        allComments.add(c);
                    }
                }
            }

            publishResults(repo, pr, installationId, allComments);

        } catch (Exception e) {
            throw new RuntimeException("PR Review failed", e);
        }
    }

    // ---------------- FILE FILTER ----------------

    private boolean shouldReview(String filename) {
        return ALLOWED_EXTENSIONS.stream()
                .anyMatch(filename::endsWith);
    }

    // ---------------- INDEXED LINE EXTRACTION ----------------

    private List<IndexedLine> extractIndexedLines(String patch) {

        List<IndexedLine> lines = new ArrayList<>();

        String[] split = patch.split("\n");
        int newLine = 0;

        for (String line : split) {

            if (line.startsWith("@@")) {
                try {
                    String[] parts = line.split(" ");
                    String newPart = parts[2];
                    newLine = Integer.parseInt(
                            newPart.split(",")[0].replace("+", "")
                    );
                } catch (Exception ignored) {}
            }
            else if (line.startsWith("+") && !line.startsWith("+++")) {

                String code = line.substring(1).trim();

                // ❗ skip useless lines
                if (code.isEmpty()
                        || code.startsWith("//")
                        || code.startsWith("package")
                        || code.startsWith("import")) {
                    newLine++;
                    continue;
                }

                lines.add(new IndexedLine(newLine, code));
                newLine++;
            }
            else if (line.startsWith(" ")) {
                newLine++;
            }
        }

        return lines;
    }

    // ---------------- CHUNKING ----------------

    private List<List<IndexedLine>> chunkByLines(List<IndexedLine> lines) {
        List<List<IndexedLine>> chunks = new ArrayList<>();
        for (IndexedLine line : lines) {
            chunks.add(List.of(line));
        }
        return chunks;
    }

    private List<List<IndexedLine>> batchChunks(List<List<IndexedLine>> chunks) {

        List<List<IndexedLine>> result = new ArrayList<>();

        List<IndexedLine> current = new ArrayList<>();
        int size = 0;

        for (List<IndexedLine> chunk : chunks) {

            int chunkSize = estimateSize(chunk);

            if (size + chunkSize > MAX_BATCH_SIZE) {
                result.add(current);
                current = new ArrayList<>();
                size = 0;
            }

            current.addAll(chunk);
            size += chunkSize;
        }

        if (!current.isEmpty()) {
            result.add(current);
        }

        return result;
    }

    private int estimateSize(List<IndexedLine> lines) {
        return lines.stream().mapToInt(l -> l.getCode().length()).sum();
    }

    // ---------------- INPUT ----------------

    private String buildIndexedInput(List<IndexedLine> lines) {

        StringBuilder sb = new StringBuilder();

        for (IndexedLine l : lines) {
            sb.append("[LINE ")
                    .append(l.getLineNumber())
                    .append("] ")
                    .append(l.getCode())
                    .append("\n");
        }

        return sb.toString();
    }

    // ---------------- 🔥 ANCHOR MAPPING ----------------

    private int mapAnchorToLine(String anchor, List<IndexedLine> lines) {

        if (lines == null || lines.isEmpty()) return 1;

        if (anchor == null || anchor.isBlank()) {
            return lines.get(0).getLineNumber();
        }

        String normAnchor = normalize(anchor);

        int bestScore = 0;
        int bestLine = lines.get(0).getLineNumber();

        for (IndexedLine line : lines) {

            String code = normalize(line.getCode());

            // exact
            if (code.equals(normAnchor)) {
                return line.getLineNumber();
            }

            // contains
            if (code.contains(normAnchor)) {
                return line.getLineNumber();
            }

            // similarity
            int score = similarityScore(normAnchor, code);

            if (score > bestScore) {
                bestScore = score;
                bestLine = line.getLineNumber();
            }
        }

        return bestLine;
    }

    private String normalize(String s) {
        return s.replaceAll("//.*", "")
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase();
    }

    private int similarityScore(String a, String b) {

        Set<String> aTokens = tokenize(a);
        Set<String> bTokens = tokenize(b);

        int score = 0;

        for (String t : aTokens) {
            if (bTokens.contains(t)) score++;
        }

        return score;
    }

    private Set<String> tokenize(String s) {
        return Arrays.stream(s.split("[^a-zA-Z0-9]+"))
                .filter(t -> t.length() > 2)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    // ---------------- 🔥 RULE ENGINE ----------------

    private List<ReviewComment> detectUnreachableCode(List<IndexedLine> lines, String fileName) {

        List<ReviewComment> result = new ArrayList<>();

        boolean seenReturn = false;

        for (IndexedLine line : lines) {

            String code = line.getCode().trim();

            if (seenReturn && !code.isEmpty()) {

                ReviewComment c = new ReviewComment();
                c.setFileName(fileName);
                c.setSeverity("ERROR");
                c.setIssue("Unreachable code detected after return statement.");
                c.setSuggestion("Remove or refactor unreachable code.");
                c.setLineNumber(line.getLineNumber());

                result.add(c);
            }

            if (code.startsWith("return")) {
                seenReturn = true;
            }
        }

        return result;
    }

    // ---------------- GITHUB ----------------

    private void publishResults(String repo,
                                int pr,
                                int installationId,
                                List<ReviewComment> comments) throws Exception {

        String sha = gitHubService.getLatestSha(repo, pr, installationId);

        boolean hasError = comments.stream()
                .anyMatch(c -> "ERROR".equalsIgnoreCase(c.getSeverity()));

        gitHubService.createCheckRun(
                repo,
                sha,
                installationId,
                !hasError,
                comments
        );
    }

    // ---------------- INTERNAL ----------------

    private static class IndexedLine {
        private final int lineNumber;
        private final String code;

        public IndexedLine(int lineNumber, String code) {
            this.lineNumber = lineNumber;
            this.code = code;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getCode() {
            return code;
        }
    }
}