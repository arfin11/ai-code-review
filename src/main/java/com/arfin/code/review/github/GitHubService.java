package com.arfin.code.review.github;

import com.arfin.code.review.model.FileDiff;
import com.arfin.code.review.model.ReviewComment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Data
public class GitHubService {

    private final HttpClient client = HttpClient.newHttpClient();
    private final GitHubTokenProvider tokenProvider;



    //System.getenv("GITHUB_TOKEN");

    private final ObjectMapper mapper = new ObjectMapper();


    public List<FileDiff> getPRFiles(String repo, int pr, int installationId) throws Exception {

        String url = "https://api.github.com/repos/" + repo + "/pulls/" + pr + "/files";

        String token = tokenProvider.getToken(installationId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode array = mapper.readTree(response.body());

        List<FileDiff> files = new ArrayList<>();

        for (JsonNode node : array) {
            if (node.get("patch").isNull()) continue;

            files.add(new FileDiff(
                    node.get("filename").asText(),
                    node.get("patch").asText()
            ));
        }

        return files;
    }

    public String getLatestSha(String repo, int pr, int installationId) throws Exception {

        String url = "https://api.github.com/repos/" + repo + "/pulls/" + pr;
        String token = tokenProvider.getToken(installationId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode json = mapper.readTree(response.body());

        return json.get("head").get("sha").asText();
    }

    public void postReview(String repo, int pr,
                           String sha,
                           int installationId, List<Map<String, Object>> comments) throws Exception {

        String url = "https://api.github.com/repos/" + repo + "/pulls/" + pr + "/reviews";

        String body = mapper.writeValueAsString(Map.of(
                "commit_id", sha,
                "event", "COMMENT",
                "comments", comments
        ));
        String token = tokenProvider.getToken(installationId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    public void setCommitStatus(String repo, String sha, int installationId, boolean success) throws Exception {

        String token = tokenProvider.getToken(installationId);

        String state = success ? "success" : "failure";

        String body = mapper.writeValueAsString(Map.of(
                "state", state,
                "context", "AI Code Review",
                "description", success ? "No blocking issues" : "Blocking issues found"
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repo + "/statuses/" + sha))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        //client.send(request, HttpResponse.BodyHandlers.ofString());

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("STATUS API RESPONSE: " + response.body());
        System.out.println("STATUS CODE: " + response.statusCode());
    }
    public void postComment(String repo, int pr, int installationId, String message) throws Exception {

        String url = "https://api.github.com/repos/" + repo + "/issues/" + pr + "/comments";

        String token = tokenProvider.getToken(installationId);

        String body = mapper.writeValueAsString(Map.of(
                "body", message
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    public void createCheckRun(String repo,
                               String sha,
                               int installationId,
                               boolean success,
                               List<ReviewComment> comments) throws Exception {

        String token = tokenProvider.getToken(installationId);

        String status = "completed";
        String conclusion = success ? "success" : "failure";

        // 🔥 Build annotations (max 50 per request)
        List<Map<String, Object>> annotations = new ArrayList<>();

        for (ReviewComment c : comments) {

            if (annotations.size() >= 50) break;

            annotations.add(Map.of(
                    "path", c.getFileName(),
                    "start_line", c.getLineNumber(),
                    "end_line", c.getLineNumber(),
                    "annotation_level", mapSeverity(c.getSeverity()),
                    "message", c.getIssue() + "\n" + c.getSuggestion()
            ));
        }

        Map<String, Object> output = Map.of(
                "title", "AI Code Review",
                "summary", buildSummary(comments),
                "annotations", annotations
        );

        String body = mapper.writeValueAsString(Map.of(
                "name", "AI Code Review",
                "head_sha", sha,
                "status", status,
                "conclusion", conclusion,
                "output", output
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repo + "/check-runs"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("CHECK RUN STATUS: " + response.statusCode());
        System.out.println("CHECK RUN BODY: " + response.body());
    }
    private String mapSeverity(String severity) {
        switch (severity.toUpperCase()) {
            case "ERROR":
                return "failure";
            case "WARNING":
                return "warning";
            default:
                return "notice";
        }
    }

    private String buildSummary(List<ReviewComment> comments) {
        StringBuilder sb = new StringBuilder();

        sb.append("### AI Review Summary\n\n");

        for (ReviewComment c : comments) {
            sb.append("- [")
                    .append(c.getSeverity())
                    .append("] ")
                    .append(c.getIssue())
                    .append("\n");
        }

        return sb.toString();
    }
}