package com.arfin.code.review.repo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "delivery_id", nullable = false, unique = true)
    private String deliveryId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ✅ REQUIRED by JPA
    public ProcessedEvent() {
    }

    public ProcessedEvent(String deliveryId) {
        this.deliveryId = deliveryId;
        this.createdAt = LocalDateTime.now();
    }

    // ✅ Getters & Setters
    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}