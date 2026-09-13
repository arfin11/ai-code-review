package com.arfin.code.review.service;

import com.arfin.code.review.model.ReviewFinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReviewAggregator {

    public List<ReviewFinding> aggregate(List<ReviewFinding>... groups) {
        log.info("Aggregating review findings from {} groups", groups == null ? 0 : groups.length);
        Map<String, ReviewFinding> unique = new LinkedHashMap<>();

        for (List<ReviewFinding> group : groups) {
            if (group == null) {
                continue;
            }

            for (ReviewFinding finding : group) {
                if (finding == null || finding.getFileName() == null || finding.getFileName().isBlank()) {
                    continue;
                }

                String key = deduplicationKey(finding);
                ReviewFinding current = unique.get(key);
                if (current == null || shouldReplace(current, finding)) {
                    unique.put(key, finding);
                }
            }
        }

        List<ReviewFinding> aggregated = suppressCommonIssues(unique.values().stream().collect(Collectors.toList())).stream()
                .sorted(Comparator.comparingInt(this::severityWeight).reversed())
                .collect(Collectors.toList());
        log.info("Review aggregation complete with {} unique findings", aggregated.size());
        return aggregated;
    }

    private List<ReviewFinding> suppressCommonIssues(List<ReviewFinding> findings) {
        Map<String, ReviewFinding> suppressed = new LinkedHashMap<>();
        for (ReviewFinding finding : findings) {
            String key = lineSuppressionKey(finding);
            ReviewFinding current = suppressed.get(key);
            if (current == null || shouldReplace(current, finding)) {
                suppressed.put(key, finding);
            }
        }
        return findingsWithStableOrder(findings, suppressed);
    }

    private String lineSuppressionKey(ReviewFinding finding) {
        return normalize(finding.getFileName()) + "::" + finding.getLineNumber();
    }

    private List<ReviewFinding> findingsWithStableOrder(List<ReviewFinding> original, Map<String, ReviewFinding> suppressed) {
        List<ReviewFinding> result = new java.util.ArrayList<>();
        for (ReviewFinding finding : original) {
            ReviewFinding selected = suppressed.get(lineSuppressionKey(finding));
            if (selected == finding && !result.contains(finding)) {
                result.add(finding);
            }
        }
        return result;
    }

    private String deduplicationKey(ReviewFinding finding) {
        String location = normalize(finding.getFileName()) + "::" + finding.getLineNumber();
        String anchor = normalize(finding.getAnchor());
        if (!anchor.isBlank()) {
            return location + "::anchor::" + anchor;
        }
        return location + "::issue::" + normalize(finding.getIssue());
    }

    private boolean shouldReplace(ReviewFinding current, ReviewFinding candidate) {
        if (isSecuritySource(candidate) && !isSecuritySource(current)) {
            return true;
        }
        if (isSecuritySource(current) && !isSecuritySource(candidate)) {
            return false;
        }
        int candidateSeverity = severityWeight(candidate);
        int currentSeverity = severityWeight(current);
        if (candidateSeverity != currentSeverity) {
            return candidateSeverity > currentSeverity;
        }
        return confidenceOf(candidate) > confidenceOf(current);
    }

    private boolean isSecuritySource(ReviewFinding finding) {
        return normalize(finding.getSource()).equals("security_review");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private double confidenceOf(ReviewFinding finding) {
        return finding != null && finding.getConfidence() != null ? finding.getConfidence() : 0.0d;
    }

    private int severityWeight(ReviewFinding finding) {
        if (finding == null || finding.getSeverity() == null) {
            return 0;
        }

        switch (finding.getSeverity().toUpperCase()) {
            case "ERROR":
                return 3;
            case "WARNING":
                return 2;
            default:
                return 1;
        }
    }
}
