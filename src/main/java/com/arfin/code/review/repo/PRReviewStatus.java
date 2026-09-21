package com.arfin.code.review.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pr_review_status", indexes = {
        @Index(name = "idx_pr_review_status_delivery_id", columnList = "delivery_id", unique = true),
        @Index(name = "idx_pr_review_status_repo_pr", columnList = "repo_name, pr_number")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PRReviewStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false, unique = true, length = 128)
    private String deliveryId;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "installation_id", nullable = false)
    private int installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReviewExecutionStatus status;

    @Column(name = "failure_reason", length = 4000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
