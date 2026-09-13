package com.arfin.code.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimiterScript;

    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public boolean allowRequest(String repository) {

        String key = "rate-limit:" + repository;
        log.info("Evaluating rate limit for repository={}, key={}", repository, key);

        Long count = redisTemplate.execute(
                rateLimiterScript,
                Collections.singletonList(key),
                "60"
        );

        boolean allowed = count != null && count <= LIMIT;
        if (allowed) {
            log.info("Rate limit check passed for repository={}, count={}", repository, count);
            return true;
        }

        log.warn("Rate limit exceeded for repository={}, count={}", repository, count);
        return false;
    }
}