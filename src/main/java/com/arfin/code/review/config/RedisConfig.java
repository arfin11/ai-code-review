package com.arfin.code.review.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<Long> rateLimiterScript() {
        return RedisScript.of(
                new ClassPathResource("scripts/rate-limiter.lua"),
                Long.class
        );
    }
}