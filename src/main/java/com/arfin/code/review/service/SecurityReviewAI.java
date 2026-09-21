package com.arfin.code.review.service;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SecurityReviewAI extends ReviewAgent {

    @Agent(outputKey = "securityFindings")
    @SystemMessage("{{systemPrompt}}")
    @UserMessage("""
        Review this code change using the security rules above.

        Code:
        {{code}}
        """)
    String review(@V("language") String language, @V("systemPrompt") String systemPrompt, @V("code") String code);

}
