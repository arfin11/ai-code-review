package com.arfin.code.review.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PRReviewStatusRepository extends JpaRepository<PRReviewStatus, Long> {
    Optional<PRReviewStatus> findByDeliveryId(String deliveryId);
}
