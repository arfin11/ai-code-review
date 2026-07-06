package com.arfin.code.review.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimiterScript;

    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public boolean allowRequest(String repository) {

        String key = "rate-limit:" + repository;

        Long count = redisTemplate.execute(
                rateLimiterScript,
                Collections.singletonList(key),
                "60"
        );

        return count != null && count <= LIMIT;
    }
}