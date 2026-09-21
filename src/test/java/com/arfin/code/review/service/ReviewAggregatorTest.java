package com.arfin.code.review.service;

import com.arfin.code.review.model.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewAggregatorTest {

    private final ReviewAggregator aggregator = new ReviewAggregator();

    @Test
    void aggregatePrefersSecurityFindingForSameLocation() {
        ReviewFinding general = finding("src/Test.java", 10, "WARNING", "General issue", "GENERAL_REVIEW", 0.8, "same");
        ReviewFinding security = finding("src/Test.java", 10, "ERROR", "Security issue", "SECURITY_REVIEW", 0.9, "same");

        List<ReviewFinding> result = aggregator.aggregate(List.of(general), List.of(security));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).isEqualTo("SECURITY_REVIEW");
        assertThat(result.get(0).getSeverity()).isEqualTo("ERROR");
    }

    @Test
    void aggregateSuppressesLowerPrioritySameLineFindings() {
        ReviewFinding warning = finding("src/Test.java", 10, "WARNING", "Warning issue", "GENERAL_REVIEW", 0.8, "a");
        ReviewFinding suggestion = finding("src/Test.java", 10, "SUGGESTION", "Suggestion issue", "GENERAL_REVIEW", 0.7, "b");

        List<ReviewFinding> result = aggregator.aggregate(List.of(warning, suggestion));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeverity()).isEqualTo("WARNING");
    }

    private ReviewFinding finding(String fileName, int lineNumber, String severity, String issue, String source, double confidence, String anchor) {
        ReviewFinding finding = new ReviewFinding();
        finding.setFileName(fileName);
        finding.setLineNumber(lineNumber);
        finding.setSeverity(severity);
        finding.setIssue(issue);
        finding.setSource(source);
        finding.setConfidence(confidence);
        finding.setAnchor(anchor);
        return finding;
    }
}
