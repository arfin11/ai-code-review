package com.arfin.code.review.service;

public final class ReviewBudgetPolicy {
    public static final int MAX_FILES_PER_PR = 40;
    public static final int MAX_CONTEXT_CHARS = 4000;
    public static final int MAX_FULL_FILE_CONTEXT_CALLS_PER_PR = 5;
    public static final int MAX_FULL_FILE_CONTEXT_CALLS_PER_FILE = 1;
    public static final int MAX_FULL_FILE_CHARS = 12000;

    private ReviewBudgetPolicy() {
    }
}
