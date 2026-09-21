package com.arfin.code.review.service;

import com.arfin.code.review.model.PRReviewEvent;
import com.arfin.code.review.repo.PRReviewStatus;
import com.arfin.code.review.repo.PRReviewStatusRepository;
import com.arfin.code.review.repo.ReviewExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PRReviewStatusService {

    private final PRReviewStatusRepository repository;

    @Transactional
    public void markReceived(PRReviewEvent event) {
        PRReviewStatus status = repository.findByDeliveryId(event.getDeliveryId())
                .orElseGet(() -> newStatus(event));
        status.setStatus(ReviewExecutionStatus.RECEIVED);
        status.setFailureReason(null);
        status.setUpdatedAt(LocalDateTime.now());
        repository.save(status);
        log.info("Marked PR review as RECEIVED for deliveryId={}", event.getDeliveryId());
    }

    @Transactional
    public void markInProgress(PRReviewEvent event) {
        PRReviewStatus status = repository.findByDeliveryId(event.getDeliveryId())
                .orElseGet(() -> newStatus(event));
        status.setStatus(ReviewExecutionStatus.IN_PROGRESS);
        status.setFailureReason(null);
        status.setUpdatedAt(LocalDateTime.now());
        repository.save(status);
        log.info("Marked PR review as IN_PROGRESS for deliveryId={}", event.getDeliveryId());
    }

    @Transactional
    public void markSuccess(PRReviewEvent event) {
        PRReviewStatus status = repository.findByDeliveryId(event.getDeliveryId())
                .orElseGet(() -> newStatus(event));
        status.setStatus(ReviewExecutionStatus.SUCCESS);
        status.setFailureReason(null);
        status.setUpdatedAt(LocalDateTime.now());
        repository.save(status);
        log.info("Marked PR review as SUCCESS for deliveryId={}", event.getDeliveryId());
    }

    @Transactional
    public void markFailed(PRReviewEvent event, Exception exception) {
        PRReviewStatus status = repository.findByDeliveryId(event.getDeliveryId())
                .orElseGet(() -> newStatus(event));
        status.setStatus(ReviewExecutionStatus.FAILED);
        status.setFailureReason(buildFailureReason(exception));
        status.setUpdatedAt(LocalDateTime.now());
        repository.save(status);
        log.error("Marked PR review as FAILED for deliveryId={}", event.getDeliveryId(), exception);
    }

    private PRReviewStatus newStatus(PRReviewEvent event) {
        LocalDateTime now = LocalDateTime.now();
        return new PRReviewStatus(
                null,
                event.getDeliveryId(),
                event.getRepo(),
                event.getPrNumber(),
                event.getInstallationId(),
                ReviewExecutionStatus.RECEIVED,
                null,
                now,
                now
        );
    }

    private String buildFailureReason(Exception exception) {
        if (exception == null) {
            return "Unknown review failure";
        }
        String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return message.length() > 4000 ? message.substring(0, 4000) : message;
    }
}
