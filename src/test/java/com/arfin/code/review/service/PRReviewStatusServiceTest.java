package com.arfin.code.review.service;

import com.arfin.code.review.model.PRReviewEvent;
import com.arfin.code.review.repo.PRReviewStatus;
import com.arfin.code.review.repo.PRReviewStatusRepository;
import com.arfin.code.review.repo.ReviewExecutionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PRReviewStatusServiceTest {

    @Mock
    private PRReviewStatusRepository repository;

    @InjectMocks
    private PRReviewStatusService service;

    @Test
    void markReceivedCreatesReceivedStatus() {
        PRReviewEvent event = event();
        when(repository.findByDeliveryId(event.getDeliveryId())).thenReturn(Optional.empty());
        when(repository.save(any(PRReviewStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markReceived(event);

        ArgumentCaptor<PRReviewStatus> captor = ArgumentCaptor.forClass(PRReviewStatus.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReviewExecutionStatus.RECEIVED);
        assertThat(captor.getValue().getRepoName()).isEqualTo(event.getRepo());
    }

    @Test
    void markFailedPersistsFailureReason() {
        PRReviewEvent event = event();
        when(repository.findByDeliveryId(event.getDeliveryId())).thenReturn(Optional.empty());
        when(repository.save(any(PRReviewStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markFailed(event, new PRReviewException("boom", new IllegalStateException("bad")));

        ArgumentCaptor<PRReviewStatus> captor = ArgumentCaptor.forClass(PRReviewStatus.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReviewExecutionStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).contains("PRReviewException");
    }

    private PRReviewEvent event() {
        PRReviewEvent event = new PRReviewEvent();
        event.setDeliveryId("delivery-1");
        event.setRepo("owner/repo");
        event.setPrNumber(11);
        event.setInstallationId(21);
        return event;
    }
}
