package com.arfin.code.review.config;

import com.arfin.code.review.service.FileContextTool;
import com.arfin.code.review.service.GeneralReviewAI;
import com.arfin.code.review.service.SecurityReviewAI;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AppConfig {

    private final FileContextTool fileContextTool;

    @Value("${openai.api.key}")
    private String apiKey;

    public AppConfig(FileContextTool fileContextTool) {
        this.fileContextTool = fileContextTool;
    }

    @Bean
    public OpenAiChatModel openAiChatModel() {
        log.info("Initializing OpenAI chat model with modelName=gpt-4o-mini");
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.2)
                .build();
    }

    @Bean
    public GeneralReviewAI generalReviewAI(OpenAiChatModel openAiChatModel) {
        log.info("Building general review agent");
        return AgenticServices.agentBuilder(GeneralReviewAI.class)
                .chatModel(openAiChatModel)
                .tools(fileContextTool)
                .build();
    }

    @Bean
    public SecurityReviewAI securityReviewAI(OpenAiChatModel openAiChatModel) {
        log.info("Building security review agent");
        return AgenticServices.agentBuilder(SecurityReviewAI.class)
                .chatModel(openAiChatModel)
                .tools(fileContextTool)
                .build();
    }
}