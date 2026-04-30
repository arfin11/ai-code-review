package com.arfin.code.review.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
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
            String jwt = jwtProvider.generateJwt();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/app/installations/" + installationId + "/access_tokens"))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Accept", "application/vnd.github+json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return mapper.readTree(res.body()).get("token").asText();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}