package com.arfin.code.review.kafka;

import com.arfin.code.review.model.PRReviewEvent;
import com.arfin.code.review.service.PRReviewException;
import com.arfin.code.review.service.PRReviewService;
import com.arfin.code.review.service.PRReviewStatusService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PRReviewConsumer {

    private static final String TRACE_ID_KEY = "traceId";
    private final PRReviewService service;
    private final PRReviewStatusService reviewStatusService;

    public PRReviewConsumer(PRReviewService service, PRReviewStatusService reviewStatusService) {
        this.service = service;
        this.reviewStatusService = reviewStatusService;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000)
    )
    @KafkaListener(topics = "${topic.pr-review}", groupId = "pr-review-group")
    public void consume(PRReviewEvent event) throws PRReviewException {
        try {
            if (event.getTraceId() != null && !event.getTraceId().isBlank()) {
                MDC.put(TRACE_ID_KEY, event.getTraceId());
            }
            reviewStatusService.markInProgress(event);
            service.reviewPR(
                    event.getRepo(),
                    event.getPrNumber(),
                    event.getInstallationId()
            );
            reviewStatusService.markSuccess(event);
        } catch (PRReviewException e) {
            reviewStatusService.markFailed(event, e);
            throw e;
        } catch (Exception e) {
            reviewStatusService.markFailed(event, e);
            throw new PRReviewException("Unexpected PR review consumer failure for deliveryId=" + event.getDeliveryId(), e);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    @DltHandler
    public void handleDlt(PRReviewEvent event) {
        try {
            if (event.getTraceId() != null && !event.getTraceId().isBlank()) {
                MDC.put(TRACE_ID_KEY, event.getTraceId());
            }
            log.error("PR review event routed to DLT after retries. deliveryId={}, repo={}, pr={}",
                    event.getDeliveryId(), event.getRepo(), event.getPrNumber());
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
