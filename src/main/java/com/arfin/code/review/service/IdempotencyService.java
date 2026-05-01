package com.arfin.code.review.service;

import com.arfin.code.review.repo.ProcessedEvent;
import com.arfin.code.review.repo.ProcessedEventRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository repo;

    public boolean isDuplicate(String id) {
        return repo.existsById(id);
    }

    public void markProcessed(String id) {
        repo.save(new ProcessedEvent(id));
    }
}