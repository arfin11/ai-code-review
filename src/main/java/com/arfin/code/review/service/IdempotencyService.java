package com.arfin.code.review.service;

import com.arfin.code.review.repo.ProcessedEvent;
import com.arfin.code.review.repo.ProcessedEventRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
@Slf4j
public class IdempotencyService {

    private final ProcessedEventRepository repo;

    public boolean isDuplicate(String id) {
        boolean duplicate = repo.existsById(id);
        if (duplicate) {
            log.info("Duplicate webhook delivery detected: {}", id);
        }
        return duplicate;
    }

    public void markProcessed(String id) {
        repo.save(new ProcessedEvent(id, LocalDateTime.now()));
        log.info("Marked webhook delivery as processed: {}", id);
    }
}