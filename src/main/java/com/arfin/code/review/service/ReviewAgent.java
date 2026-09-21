package com.arfin.code.review.service;

public interface ReviewAgent {
    String review(String language, String systemPrompt, String code);
}
