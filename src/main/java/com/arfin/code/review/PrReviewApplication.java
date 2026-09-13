package com.arfin.code.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
@Slf4j
public class PrReviewApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        log.info("Starting AI Code Review application in Asia/Kolkata timezone");
        SpringApplication.run(PrReviewApplication.class, args);
    }
}