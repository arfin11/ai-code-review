package com.arfin.code.review.model;

import lombok.Data;

import java.util.List;

@Data
public class ReviewResponse {
        private List<ReviewComment> comments;
}