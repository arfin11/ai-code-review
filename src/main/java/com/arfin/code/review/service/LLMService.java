package com.arfin.code.review.service;

import com.arfin.code.review.config.AppConfig;
import com.arfin.code.review.model.ReviewComment;
import com.arfin.code.review.model.ReviewResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
@Data
public class LLMService {

    private final AppConfig appConfig;
    private final ObjectMapper mapper = new ObjectMapper();


    public ReviewResponse review(String diff, String filename) {

        try {

            String response = appConfig.codeReviewAI().review("File: " + filename + "\n\nDiff:\n" + diff);

            // 🔥 Clean markdown if model adds it
            response = response.replace("```json", "")
                    .replace("```", "")
                    .trim();

            JsonNode root = mapper.readTree(response);

            List<ReviewComment> comments = new ArrayList<>();

            for (JsonNode node : root.path("comments")) {

                ReviewComment c = new ReviewComment();
                c.setSeverity(node.path("severity").asText());
                c.setIssue(node.path("issue").asText());
                c.setSuggestion(node.path("suggestion").asText());
                c.setLineHint(node.path("lineHint").asText());

                comments.add(c);
            }

            ReviewResponse res = new ReviewResponse();
            res.setComments(comments);

            return res;

        } catch (Exception e) {
            e.printStackTrace();
            return new ReviewResponse();
        }
    }
}