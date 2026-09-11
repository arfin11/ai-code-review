package com.arfin.code.review.service;

import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

public interface ParallelReviewWorkflow {
    @ParallelAgent(
            name = "parallelReviewWorkflow",
            description = "Runs general and security review agents concurrently for the same review payload.",
            subAgents = {GeneralReviewAI.class, SecurityReviewAI.class}
    )
    ResultWithAgenticScope run(@V("code") String code);
}
