package com.arfin.code.review.controller;

import com.arfin.code.review.kafka.PRReviewProducer;
import com.arfin.code.review.model.PRReviewEvent;
import com.arfin.code.review.service.IdempotencyService;
import com.arfin.code.review.service.PRReviewStatusService;
import com.arfin.code.review.service.RateLimiterService;
import com.arfin.code.review.util.SignatureValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class GitHubWebhookController {

    private static final Set<String> SUPPORTED_ACTIONS = Set.of("labeled", "synchronize");
    private static final String TRACE_ID_KEY = "traceId";

    private final PRReviewProducer producer;
    private final IdempotencyService idempotencyService;
    private final RateLimiterService rateLimiterService;
    private final PRReviewStatusService reviewStatusService;
    private final ObjectMapper mapper;

    @Value("${github.webhook.secret}")
    private String secret;

    @PostMapping
    public ResponseEntity<String> handle(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestBody byte[] payloadBytes) {
        try {
            log.info("Request received for event: {} with signature {}", event, signature);

            if (!SignatureValidator.isValid(payloadBytes, signature, secret)) {
                log.warn("Invalid GitHub webhook signature for delivery {}", deliveryId);
                return ResponseEntity.status(401).body("Invalid signature");
            }

            if (!"pull_request".equals(event)) {
                log.info("Ignoring non-pull_request webhook event: {}", event);
                return ResponseEntity.ok("Ignored");
            }

            JsonNode payload = mapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            String action = payload.path("action").asText();

            if (!SUPPORTED_ACTIONS.contains(action)) {
                log.info("Ignoring unsupported pull_request action: {}", action);
                return ResponseEntity.ok("Ignored");
            }

            if ("labeled".equals(action) && !"ai-review".equals(payload.path("label").path("name").asText())) {
                return ResponseEntity.ok("Wrong label");
            }

            String repo = payload.path("repository").path("full_name").asText();
            int installationId = extractInstallationId(payload);
            if (installationId == -1) {
                log.info("Installation ID missing in webhook payload for delivery {}", deliveryId);
                return ResponseEntity.ok("Ignored - no installation id");
            }

            if (!rateLimiterService.allowRequest(repo)) {
                log.warn("Rate limit exceeded for repo {}", repo);
                return ResponseEntity.status(429).body("Rate limit exceeded");
            }

            if (idempotencyService.isDuplicate(deliveryId)) {
                log.info("Duplicate event ignored: {}", deliveryId);
                return ResponseEntity.ok("Duplicate");
            }

            PRReviewEvent eventObj = buildReviewEvent(payload, repo, installationId, deliveryId);
            reviewStatusService.markReceived(eventObj);
            producer.publish(eventObj);
            idempotencyService.markProcessed(deliveryId);

            return ResponseEntity.ok("Triggered");
        } catch (Exception e) {
            log.error("Exception occurred while processing webhook event {}", deliveryId, e);
            return ResponseEntity.internalServerError().body("Error");
        }
    }

    private int extractInstallationId(JsonNode payload) {
        JsonNode installationNode = payload.path("installation");
        if (installationNode.isMissingNode() || installationNode.path("id").isMissingNode()) {
            return -1;
        }
        return installationNode.path("id").asInt();
    }

    private PRReviewEvent buildReviewEvent(JsonNode payload, String repo, int installationId, String deliveryId) {
        PRReviewEvent eventObj = new PRReviewEvent();
        eventObj.setRepo(repo);
        eventObj.setPrNumber(payload.path("pull_request").path("number").asInt());
        eventObj.setInstallationId(installationId);
        eventObj.setDeliveryId(deliveryId);
        eventObj.setTraceId(MDC.get(TRACE_ID_KEY));
        return eventObj;
    }
}
