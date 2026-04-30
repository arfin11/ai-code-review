package com.arfin.code.review.config;

import com.arfin.code.review.service.CodeReviewAI;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${openai.api.key}")
    private String apiKey;

    @Bean
    public CodeReviewAI codeReviewAI() {

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.2)
                .build();

        return AiServices.create(CodeReviewAI.class, model);
    }
}