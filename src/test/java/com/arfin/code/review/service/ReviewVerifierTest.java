package com.arfin.code.review.service;

import com.arfin.code.review.model.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewVerifierTest {

    private final ReviewVerifier verifier = new ReviewVerifier();

    @Test
    void verifyNormalizesSeverityAndFiltersLowConfidence() {
        ReviewFinding valid = finding("src/Test.java", 12, "warning", "Potential issue", "repo.runQuery(\"select * from test\")", 0.9);
        ReviewFinding lowConfidence = finding("src/Test.java", 13, "error", "Potential issue", "repo.runQuery(\"select * from test\")", 0.2);

        List<ReviewFinding> findings = verifier.verify(List.of(valid, lowConfidence));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void verifyFiltersUnsupportedSqlInjectionFindings() {
        ReviewFinding sqlOnFindById = finding("src/Test.java", 12, "error", "SQL injection vulnerability", "repo.findById(id)", 0.95);

        List<ReviewFinding> findings = verifier.verify(List.of(sqlOnFindById));

        assertThat(findings).isEmpty();
    }

    private ReviewFinding finding(String fileName, int lineNumber, String severity, String issue, String anchor, double confidence) {
        ReviewFinding finding = new ReviewFinding();
        finding.setFileName(fileName);
        finding.setLineNumber(lineNumber);
        finding.setSeverity(severity);
        finding.setIssue(issue);
        finding.setAnchor(anchor);
        finding.setConfidence(confidence);
        return finding;
    }
}
