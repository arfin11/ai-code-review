package com.arfin.code.review.controller;

import com.arfin.code.review.kafka.PRReviewProducer;
import com.arfin.code.review.service.IdempotencyService;
import com.arfin.code.review.service.PRReviewStatusService;
import com.arfin.code.review.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubWebhookControllerTest {

    @Mock
    private PRReviewProducer producer;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private PRReviewStatusService reviewStatusService;

    @InjectMocks
    private GitHubWebhookController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "secret", "secret");
        ReflectionTestUtils.setField(controller, "mapper", new ObjectMapper());
    }

    @Test
    void handleReturnsIgnoredForUnsupportedAction() throws Exception {
        byte[] payload = """
                {
                  "action":"opened",
                  "repository":{"full_name":"owner/repo"},
                  "installation":{"id":11},
                  "pull_request":{"number":5}
                }
                """.getBytes(StandardCharsets.UTF_8);

        ResponseEntity<String> response = controller.handle("pull_request", sign(payload), "delivery-1", payload);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("Ignored");
        verify(producer, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleReturnsDuplicateWhenDeliveryAlreadyProcessed() throws Exception {
        byte[] payload = """
                {
                  "action":"synchronize",
                  "repository":{"full_name":"owner/repo"},
                  "installation":{"id":11},
                  "pull_request":{"number":5}
                }
                """.getBytes(StandardCharsets.UTF_8);
        when(rateLimiterService.allowRequest("owner/repo")).thenReturn(true);
        when(idempotencyService.isDuplicate("delivery-1")).thenReturn(true);

        ResponseEntity<String> response = controller.handle("pull_request", sign(payload), "delivery-1", payload);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("Duplicate");
        verify(producer, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    private String sign(byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(payload);
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) {
            String value = Integer.toHexString(0xff & b);
            if (value.length() == 1) {
                hex.append('0');
            }
            hex.append(value);
        }
        return "sha256=" + hex;
    }
}
