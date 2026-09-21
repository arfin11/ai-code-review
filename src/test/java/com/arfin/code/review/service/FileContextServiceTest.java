package com.arfin.code.review.service;

import com.arfin.code.review.model.FileDiff;
import com.arfin.code.review.model.ReviewContext;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileContextServiceTest {

    private final FileContextService service = new FileContextService();

    @Test
    void buildContextsExtractsChangedLinesAndSnippet() {
        FileDiff diff = new FileDiff("src/main/java/Test.java", "@@ -1,2 +1,4 @@\n public class Test {\n+    private final String value = \"x\";\n+    void run() {}\n }");

        List<ReviewContext> contexts = service.buildContexts(List.of(diff));

        assertThat(contexts).hasSize(1);
        ReviewContext context = contexts.get(0);
        assertThat(context.getChangedLines()).containsExactly(2, 3);
        assertThat(context.getSnippet()).contains("[LINE 2]     private final String value = \"x\";");
        assertThat(context.getSnippet()).contains("[LINE 3]     void run() {}");
    }

    @Test
    void buildContextsSkipsInvalidEntries() {
        List<FileDiff> files = Arrays.asList(new FileDiff(null, "patch"), null);

        List<ReviewContext> contexts = service.buildContexts(files);
        assertThat(contexts).isEmpty();
    }
}
