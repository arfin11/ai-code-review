package com.arfin.code.review.service;

import com.arfin.code.review.model.FileDiff;
import com.arfin.code.review.model.ReviewContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FileContextService {

    private static final int MAX_FILES_PER_PR = ReviewBudgetPolicy.MAX_FILES_PER_PR;
    private static final int MAX_CONTEXT_CHARS = ReviewBudgetPolicy.MAX_CONTEXT_CHARS;

    public List<ReviewContext> buildContexts(List<FileDiff> files) {
        List<FileDiff> boundedFiles = files == null ? List.of() : files.stream().limit(MAX_FILES_PER_PR).toList();
        List<ReviewContext> contexts = new ArrayList<>();

        for (FileDiff file : boundedFiles) {
            if (file == null || file.getFilename() == null || file.getFilename().isBlank()) {
                continue;
            }

            List<Integer> changedLines = extractChangedLineNumbers(file.getPatch());
            String baseSnippet = buildPatchSnippet(file.getPatch(), changedLines);

            ReviewContext context = new ReviewContext();
            context.setFileName(file.getFilename());
            context.setPatch(file.getPatch());
            context.setSnippet(baseSnippet);
            context.setChangedLines(changedLines);
            context.setFileContent(baseSnippet);
            contexts.add(context);
        }

        return contexts;
    }

    private String buildPatchSnippet(String patch, List<Integer> changedLines) {
        if (patch == null || patch.isBlank()) {
            return "";
        }

        StringBuilder snippet = new StringBuilder();
        snippet.append("[CHANGED]\n");

        int currentLine = 0;
        for (String rawLine : patch.split("\\R")) {
            if (rawLine.startsWith("@@")) {
                currentLine = parseStartLine(rawLine);
                continue;
            }

            if (rawLine.startsWith("+") && !rawLine.startsWith("+++")) {
                int lineNumber = currentLine;
                if (lineNumber > 0) {
                    appendLine(snippet, lineNumber, rawLine.substring(1), true);
                }
                currentLine++;
                continue;
            }

            if (rawLine.startsWith(" ")) {
                currentLine++;
                continue;
            }

            if (rawLine.startsWith("-")) {
                continue;
            }
        }

        String result = snippet.toString();
        return result.length() > MAX_CONTEXT_CHARS ? result.substring(0, MAX_CONTEXT_CHARS) : result;
    }

    private void appendLine(StringBuilder snippet, int lineNumber, String content, boolean changed) {
        if (lineNumber <= 0 || content == null) {
            return;
        }
        snippet.append(changed ? "[LINE " : "[CONTEXT ")
                .append(lineNumber)
                .append("] ")
                .append(content)
                .append(System.lineSeparator());
    }

    private List<Integer> extractChangedLineNumbers(String patch) {
        List<Integer> lineNumbers = new ArrayList<>();
        if (patch == null || patch.isBlank()) {
            return lineNumbers;
        }

        int currentLine = 0;
        for (String rawLine : patch.split("\\R")) {
            if (rawLine.startsWith("@@")) {
                currentLine = parseStartLine(rawLine);
                continue;
            }

            if (rawLine.startsWith("+") && !rawLine.startsWith("+++")) {
                String code = rawLine.substring(1).trim();
                if (!code.isEmpty() && !code.startsWith("//") && !code.startsWith("package") && !code.startsWith("import")) {
                    lineNumbers.add(currentLine);
                }
                currentLine++;
                continue;
            }

            if (rawLine.startsWith(" ")) {
                currentLine++;
            }
        }

        return lineNumbers;
    }

    private int parseStartLine(String hunkHeader) {
        try {
            String[] parts = hunkHeader.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("+") && !part.startsWith("+++")) {
                    String value = part.substring(1).split(",")[0];
                    return Integer.parseInt(value);
                }
            }
        } catch (NumberFormatException ignored) {
            // ignore malformed hunks
        }
        return 1;
    }
}
