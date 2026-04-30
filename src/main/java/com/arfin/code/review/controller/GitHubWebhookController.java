package com.arfin.code.review.controller;

import com.arfin.code.review.service.PRReviewService;
import com.arfin.code.review.util.SignatureValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/webhook")
public class GitHubWebhookController {

    private final PRReviewService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${github.webhook.secret}")
    private String secret;

    public GitHubWebhookController(PRReviewService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<String> handle(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] payloadBytes
    ) {
        try {
            // ✅ Signature validation
            if (!SignatureValidator.isValid(payloadBytes, signature, secret)) {
                return ResponseEntity.status(401).body("Invalid signature");
            }

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);

            // ✅ Only handle PR events
            if (!"pull_request".equals(event)) {
                return ResponseEntity.ok("Ignored");
            }

            JsonNode root = mapper.readTree(payload);

            String action = root.path("action").asText();

            // 🚀 ✅ Handle BOTH cases
            if (!List.of("labeled", "synchronize").contains(action)) {
                return ResponseEntity.ok("Ignored");
            }

            // 🚨 Only check label for "labeled" event
            if ("labeled".equals(action)) {
                String label = root.path("label").path("name").asText();

                if (!"ai-review".equals(label)) {
                    return ResponseEntity.ok("Wrong label");
                }
            }

            // ✅ Extract repo + PR
            String repo = root.path("repository").path("full_name").asText();
            int pr = root.path("pull_request").path("number").asInt();

            // ✅ Extract installation ID safely
            JsonNode installationNode = root.path("installation");

            if (installationNode.isMissingNode() || installationNode.path("id").isMissingNode()) {
                System.out.println("❌ Installation ID missing in webhook payload");
                return ResponseEntity.ok("Ignored - no installation id");
            }

            int installationId = installationNode.path("id").asInt();

            // 🚀 Trigger review
            service.reviewPR(repo, pr, installationId);

            return ResponseEntity.ok("Triggered");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error");
        }
    }
}