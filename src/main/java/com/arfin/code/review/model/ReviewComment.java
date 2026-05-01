package com.arfin.code.review.model;

import lombok.Data;

@Data
public class ReviewComment {
    private String severity;
    private String issue;
    private String suggestion;
    private String lineHint;
    private String fileName;
    private String anchor;
    private Integer lineNumber;
}