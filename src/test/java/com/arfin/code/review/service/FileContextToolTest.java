package com.arfin.code.review.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class FileContextToolTest {

    @Test
    void reviewSessionTracksFullFileContextBudget() throws Exception {
        FileContextTool tool = new FileContextTool(installationId -> "token");
        try (AutoCloseable ignored = tool.openReviewSession("owner/repo", 42, 7)) {
            Class<?> reviewSessionType = Class.forName("com.arfin.code.review.service.FileContextTool$ReviewSession");
            Method requireSession = FileContextTool.class.getDeclaredMethod("requireReviewSession");
            requireSession.setAccessible(true);
            Object session = requireSession.invoke(tool);

            Method canFetchFullFileContext = FileContextTool.class.getDeclaredMethod("canFetchFullFileContext", reviewSessionType, String.class);
            canFetchFullFileContext.setAccessible(true);

            assertThat((boolean) canFetchFullFileContext.invoke(tool, session, "src/main/java/Full.java")).isTrue();
            for (int i = 1; i < ReviewBudgetPolicy.MAX_FULL_FILE_CONTEXT_CALLS_PER_PR; i++) {
                assertThat((boolean) canFetchFullFileContext.invoke(tool, session, "src/main/java/Full" + i + ".java")).isTrue();
            }
            assertThat((boolean) canFetchFullFileContext.invoke(tool, session, "src/main/java/FullBlocked.java")).isFalse();
        }
    }

    @Test
    void budgetPolicyUsesReasonableFullFileLimits() {
        assertThat(ReviewBudgetPolicy.MAX_FILES_PER_PR).isGreaterThan(0);
        assertThat(ReviewBudgetPolicy.MAX_FULL_FILE_CONTEXT_CALLS_PER_PR).isGreaterThan(0);
        assertThat(ReviewBudgetPolicy.MAX_FULL_FILE_CONTEXT_CALLS_PER_FILE).isGreaterThan(0);
        assertThat(ReviewBudgetPolicy.MAX_FULL_FILE_CHARS).isGreaterThan(ReviewBudgetPolicy.MAX_CONTEXT_CHARS);
    }
}
