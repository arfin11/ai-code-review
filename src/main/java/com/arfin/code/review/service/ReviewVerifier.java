package com.arfin.code.review.service;

import com.arfin.code.review.model.ReviewFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewVerifier {

    public List<ReviewFinding> verify(List<ReviewFinding> findings) {
        List<ReviewFinding> valid = new ArrayList<>();

        for (ReviewFinding finding : findings) {
            if (finding == null) {
                continue;
            }

            normalizeSeverity(finding);

            if (finding.getFileName() == null || finding.getFileName().isBlank()) {
                continue;
            }

            if (finding.getLineNumber() == null || finding.getLineNumber() <= 0) {
                continue;
            }

            if (finding.getIssue() == null || finding.getIssue().isBlank()) {
                continue;
            }

            if (finding.getConfidence() != null && finding.getConfidence() < 0.7) {
                continue;
            }

            if (isUnsupportedSqlInjectionFinding(finding)) {
                continue;
            }

            valid.add(finding);
        }

        return valid;
    }

    private boolean isUnsupportedSqlInjectionFinding(ReviewFinding finding) {
        String issue = normalize(finding.getIssue());
        if (!issue.contains("sql injection")) {
            return false;
        }

        String anchor = normalize(finding.getAnchor());
        if (anchor.contains("findbyid(") || anchor.contains("save(") || anchor.contains("findall(")) {
            return true;
        }

        return !anchor.contains("select ")
                && !anchor.contains("insert ")
                && !anchor.contains("update ")
                && !anchor.contains("delete ")
                && !anchor.contains("createquery")
                && !anchor.contains("createnativequery")
                && !anchor.contains("jdbctemplate")
                && !anchor.contains("entitymanager")
                && !anchor.contains("@query");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private void normalizeSeverity(ReviewFinding finding) {
        String severity = normalize(finding.getSeverity());
        if (severity.isBlank()) {
            finding.setSeverity("SUGGESTION");
            return;
        }

        switch (severity) {
            case "critical":
            case "high":
            case "error":
                finding.setSeverity("ERROR");
                return;
            case "medium":
            case "warning":
                finding.setSeverity("WARNING");
                return;
            case "low":
            case "suggestion":
            case "notice":
                finding.setSeverity("SUGGESTION");
                return;
            default:
                finding.setSeverity("SUGGESTION");
        }
    }
}
