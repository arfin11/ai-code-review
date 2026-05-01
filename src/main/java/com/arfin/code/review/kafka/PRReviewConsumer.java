package com.arfin.code.review.kafka;

import com.arfin.code.review.controller.GitHubWebhookController;
import com.arfin.code.review.model.PRReviewEvent;
import com.arfin.code.review.service.PRReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class PRReviewConsumer {

    private final PRReviewService service;

    private static final Logger log = LoggerFactory.getLogger(PRReviewConsumer.class);

    public PRReviewConsumer(PRReviewService service) {
        this.service = service;
    }
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000)
    )
    @KafkaListener(topics = "${topic.pr-review}", groupId = "pr-review-group")
    public void consume(PRReviewEvent event) {

        try {
            service.reviewPR(
                event.getRepo(),
                event.getPrNumber(),
                event.getInstallationId()
            );
        } catch (Exception e) {
            log.error("An unexpected error occurred while processing webhook event {0}",e);
        }
    }
}