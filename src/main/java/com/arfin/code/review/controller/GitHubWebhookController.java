package com.arfin.code.review.controller;

import com.arfin.code.review.kafka.PRReviewProducer;
import com.arfin.code.review.model.PRReviewEvent;
import com.arfin.code.review.service.IdempotencyService;
import com.arfin.code.review.service.PRReviewService;
import com.arfin.code.review.util.SignatureValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/webhook")
public class GitHubWebhookController {

    private final PRReviewProducer producer;
    private final IdempotencyService idempotencyService;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);
    @Value("${github.webhook.secret}")
    private String secret;

    public GitHubWebhookController(PRReviewProducer producer,
                                   IdempotencyService idempotencyService) {
        this.producer = producer;
        this.idempotencyService = idempotencyService;
    }
    @PostMapping
    public ResponseEntity<String> handle(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestBody byte[] payloadBytes
    ) {
        try {
            log.info("request received with event: {} and signature {}",event,signature);
            // ✅ Signature validation
            if (!SignatureValidator.isValid(payloadBytes, signature, secret)) {
                log.info("Invalid request, Hence returning");
                return ResponseEntity.status(401).body("Invalid signature");
            }

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);

            // ✅ Only handle PR events
            if (!"pull_request".equals(event)) {
                log.info("Ignoring other webhook events.");
                return ResponseEntity.ok("Ignored");
            }

            JsonNode root = mapper.readTree(payload);

            String action = root.path("action").asText();

            // 🚀 ✅ Handle BOTH cases
            if (!List.of("labeled", "synchronize").contains(action)) {
                log.info("Ignoring other webhook action events {}.",action);
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
                log.info("❌ Installation ID missing in webhook payload");
                return ResponseEntity.ok("Ignored - no installation id");
            }

            int installationId = installationNode.path("id").asInt();

            if (idempotencyService.isDuplicate(deliveryId)) {
                log.info("Duplicate event ignored: {}", deliveryId);
                return ResponseEntity.ok("Duplicate");
            }

            PRReviewEvent eventObj = new PRReviewEvent();
            eventObj.setRepo(repo);
            eventObj.setPrNumber(pr);
            eventObj.setInstallationId(installationId);
            eventObj.setDeliveryId(deliveryId);

            producer.publish(eventObj);

            idempotencyService.markProcessed(deliveryId);

            return ResponseEntity.ok("Triggered");

        } catch (Exception e) {
            log.error("exception occured while processing webhook event.",e);
            return ResponseEntity.internalServerError().body("Error");
        }
    }
}