package com.arfin.code.review.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResult {
    private ReviewResponse generalFindings;
    private ReviewResponse securityFindings;
}
