package com.arfin.code.review.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@Slf4j
public class GitHubAppTokenProvider implements GitHubTokenProvider {

    private final GitHubJwtProvider jwtProvider;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubAppTokenProvider(GitHubJwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public String getToken(int installationId) {
        try {
            log.info("Requesting GitHub app installation token for installationId={}", installationId);
            String jwt = jwtProvider.generateJwt();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/app/installations/" + installationId + "/access_tokens"))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Accept", "application/vnd.github+json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            String body = res.body();
            if (res.statusCode() >= 300) {
                log.error("Failed to create GitHub installation token for installationId={}, status={}, body={}", installationId, res.statusCode(), body);
                throw new IllegalStateException("Failed to create GitHub installation token for installationId="
                        + installationId + ": HTTP " + res.statusCode() + " body=" + body);
            }

            JsonNode root = mapper.readTree(body);
            JsonNode tokenNode = root.get("token");
            if (tokenNode == null || tokenNode.isNull() || tokenNode.asText() == null || tokenNode.asText().isBlank()) {
                log.error("GitHub installation token response missing token field for installationId={}", installationId);
                throw new IllegalStateException("GitHub installation token response did not contain a 'token' field for installationId="
                        + installationId + ": body=" + body);
            }
            log.info("GitHub installation token issued successfully for installationId={}", installationId);
            return tokenNode.asText();

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unable to fetch GitHub installation token for installationId={}", installationId, e);
            throw new RuntimeException("Unable to fetch GitHub installation token for installationId=" + installationId, e);
        }
    }
}