package com.arfin.code.review.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class ReviewPromptService {

    private static final String DEFAULT_LANGUAGE = "java";
    private final ResourceLoader resourceLoader;

    public ReviewPromptService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String generalPrompt(String language) {
        return loadPrompt(language, "general-review.txt");
    }

    public String securityPrompt(String language) {
        return loadPrompt(language, "security-review.txt");
    }

    private String loadPrompt(String language, String fileName) {
        String normalizedLanguage = normalizeLanguage(language);
        Resource resource = resourceLoader.getResource("classpath:prompts/" + normalizedLanguage + "/" + fileName);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt file not found for language='" + normalizedLanguage + "' and file='" + fileName + "'");
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt file for language='" + normalizedLanguage + "' and file='" + fileName + "'", e);
        }
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return language.trim().toLowerCase();
    }
}
