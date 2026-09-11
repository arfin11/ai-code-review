package com.arfin.code.review.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewFinding {
    private String fileName;
    private Integer lineNumber;
    private String severity;
    private String issue;
    private String suggestion;
    private String source;
    private Double confidence;
    private String anchor;
}
