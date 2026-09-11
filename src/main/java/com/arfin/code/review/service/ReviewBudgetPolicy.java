package com.arfin.code.review.service;

public final class ReviewBudgetPolicy {
    public static final int MAX_FILES_PER_PR = 40;
    public static final int MAX_CONTEXT_CHARS = 4000;
    public static final int MAX_SURROUNDING_LINES = 30;
    public static final int MAX_EXTRA_CONTEXT_CALLS_PER_PR = 20;
    public static final int MAX_EXTRA_CONTEXT_CALLS_PER_FILE = 3;

    private ReviewBudgetPolicy() {
    }
}
