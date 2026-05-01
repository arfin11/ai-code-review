package com.arfin.code.review.model;

import lombok.Data;

@Data
public class PRReviewEvent {

    private String repo;
    private int prNumber;
    private int installationId;
    private String deliveryId; // important for idempotency

    // getters/setters
}