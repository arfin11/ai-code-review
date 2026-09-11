package com.arfin.code.review.service;

import com.arfin.code.review.github.GitHubTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FileContextTool {

    private final GitHubTokenProvider tokenProvider;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, AtomicInteger> prContextCallCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> fileContextCallCounts = new ConcurrentHashMap<>();
    private final InheritableThreadLocal<ReviewSession> reviewSession = new InheritableThreadLocal<>();

    public FileContextTool(GitHubTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public AutoCloseable openReviewSession(String repo, int installationId, int prNumber) {
        ReviewSession session = new ReviewSession(repo, installationId, prNumber);
        reviewSession.set(session);
        return () -> closeReviewSession(session);
    }

    @Tool("Fetch a small surrounding code snippet around a changed line in the current pull request file when more local context is needed for review.")
    public String fetchContext(String filePath, int targetLine) throws Exception {
        ReviewSession session = requireReviewSession();
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        if (targetLine <= 0) {
            return null;
        }
        if (!canFetchExtraContext(session, filePath)) {
            return "Context fetch skipped: extra context budget exceeded for this PR/file.";
        }

        String fileContent = fetchFileContent(session.repo(), filePath, session.installationId());
        if (fileContent == null || fileContent.isBlank()) {
            return null;
        }

        String[] lines = fileContent.split("\\R");
        int start = Math.max(1, targetLine - ReviewBudgetPolicy.MAX_SURROUNDING_LINES);
        int end = Math.min(lines.length, targetLine + ReviewBudgetPolicy.MAX_SURROUNDING_LINES);

        StringBuilder snippet = new StringBuilder();
        for (int i = start; i <= end; i++) {
            snippet.append("[LINE ").append(i).append("] ").append(lines[i - 1]).append("\n");
        }

        String result = snippet.toString();
        return result.length() > ReviewBudgetPolicy.MAX_CONTEXT_CHARS
                ? result.substring(0, ReviewBudgetPolicy.MAX_CONTEXT_CHARS)
                : result;
    }

    private boolean canFetchExtraContext(ReviewSession session, String filePath) {
        String prKey = session.prKey();
        String fileKey = prKey + ":file:" + filePath;

        AtomicInteger prCalls = prContextCallCounts.computeIfAbsent(prKey, key -> new AtomicInteger(0));
        AtomicInteger fileCalls = fileContextCallCounts.computeIfAbsent(fileKey, key -> new AtomicInteger(0));

        if (prCalls.get() >= ReviewBudgetPolicy.MAX_EXTRA_CONTEXT_CALLS_PER_PR
                || fileCalls.get() >= ReviewBudgetPolicy.MAX_EXTRA_CONTEXT_CALLS_PER_FILE) {
            return false;
        }

        prCalls.incrementAndGet();
        fileCalls.incrementAndGet();
        return true;
    }

    private ReviewSession requireReviewSession() {
        ReviewSession session = reviewSession.get();
        if (session == null) {
            throw new IllegalStateException("No active PR review session is available for fetchContext");
        }
        return session;
    }

    private void closeReviewSession(ReviewSession session) {
        ReviewSession activeSession = reviewSession.get();
        if (Objects.equals(activeSession, session)) {
            reviewSession.remove();
        }
        prContextCallCounts.remove(session.prKey());
        String fileKeyPrefix = session.prKey() + ":file:";
        fileContextCallCounts.keySet().removeIf(key -> key.startsWith(fileKeyPrefix));
    }

    private String fetchFileContent(String repo, String path, int installationId) throws Exception {
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        String url = "https://api.github.com/repos/" + repo + "/contents/" + encodedPath;
        String token = tokenProvider.getToken(installationId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            String body = response.body();
            throw new IllegalStateException("GitHub file fetch failed for repo=" + repo + ", path=" + path
                    + ", installationId=" + installationId + ", status=" + response.statusCode()
                    + ", body=" + body);
        }

        JsonNode node = mapper.readTree(response.body());
        JsonNode contentNode = node.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }

        String content = contentNode.asText();
        if (content == null || content.isBlank()) {
            return null;
        }
        return new String(java.util.Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
    }

    private record ReviewSession(String repo, int installationId, int prNumber) {

        private String prKey() {
            return repo + ":" + installationId + ":pr:" + prNumber;
        }
    }
}
