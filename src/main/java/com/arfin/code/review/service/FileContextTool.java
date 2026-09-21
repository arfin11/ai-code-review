package com.arfin.code.review.service;

import com.arfin.code.review.github.GitHubTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class FileContextTool {

    private final GitHubTokenProvider tokenProvider;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, AtomicInteger> prFullFileContextCallCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> fileFullFileContextCallCounts = new ConcurrentHashMap<>();
    private final InheritableThreadLocal<ReviewSession> reviewSession = new InheritableThreadLocal<>();

    public FileContextTool(GitHubTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public AutoCloseable openReviewSession(String repo, int installationId, int prNumber) {
        ReviewSession session = new ReviewSession(repo, installationId, prNumber);
        reviewSession.set(session);
        log.info("Opened review session for repo={}, installationId={}, prNumber={}", repo, installationId, prNumber);
        return () -> closeReviewSession(session);
    }

    @Tool("Fetch the full content of a file in the current PR when file-level review context is required.")
    public String fetchFullFile(String filePath) throws Exception {
        ReviewSession session = requireReviewSession();
        if (filePath == null || filePath.isBlank()) {
            log.warn("fetchFullFile called with blank filePath for review session={}", session);
            return null;
        }
        if (!canFetchFullFileContext(session, filePath)) {
            log.warn("Full-file context budget exceeded for repo={}, prNumber={}, filePath={}", session.repo(), session.prNumber(), filePath);
            return "Full-file context fetch skipped: full-file budget exceeded for this PR/file.";
        }

        String fileContent = fetchFileContent(session.repo(), filePath, session.installationId());
        if (fileContent == null || fileContent.isBlank()) {
            log.warn("No file content available for repo={}, filePath={}", session.repo(), filePath);
            return null;
        }

        String result = fileContent.length() > ReviewBudgetPolicy.MAX_FULL_FILE_CHARS
                ? fileContent.substring(0, ReviewBudgetPolicy.MAX_FULL_FILE_CHARS)
                : fileContent;
        log.info("Fetched full file for repo={}, prNumber={}, filePath={}, chars={}",
                session.repo(), session.prNumber(), filePath, result.length());
        return result;
    }

    private boolean canFetchFullFileContext(ReviewSession session, String filePath) {
        String prKey = session.prKey();
        String fileKey = prKey + ":fullfile:" + filePath;

        AtomicInteger prCalls = prFullFileContextCallCounts.computeIfAbsent(prKey, key -> new AtomicInteger(0));
        AtomicInteger fileCalls = fileFullFileContextCallCounts.computeIfAbsent(fileKey, key -> new AtomicInteger(0));

        if (prCalls.get() >= ReviewBudgetPolicy.MAX_FULL_FILE_CONTEXT_CALLS_PER_PR
                || fileCalls.get() >= ReviewBudgetPolicy.MAX_FULL_FILE_CONTEXT_CALLS_PER_FILE) {
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
        prFullFileContextCallCounts.remove(session.prKey());
        String fullFileKeyPrefix = session.prKey() + ":fullfile:";
        fileFullFileContextCallCounts.keySet().removeIf(key -> key.startsWith(fullFileKeyPrefix));
        log.info("Closed review session for repo={}, installationId={}, prNumber={}", session.repo(), session.installationId(), session.prNumber());
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
            log.error("GitHub file fetch failed for repo={}, path={}, installationId={}, status={}, body={}", repo, path, installationId, response.statusCode(), body);
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
