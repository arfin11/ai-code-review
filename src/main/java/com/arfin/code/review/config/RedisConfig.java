package com.arfin.code.review.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
@Slf4j
public class RedisConfig {

    @Bean
    public RedisScript<Long> rateLimiterScript() {
        log.info("Loading Redis rate limiter Lua script");
        return RedisScript.of(
                new ClassPathResource("scripts/rate-limiter.lua"),
                Long.class
        );
    }
}