package com.arfin.code.review.service;

import com.arfin.code.review.model.DiffLine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DiffParserService {

    public List<DiffLine> extractAddedLines(String patch) {

        List<DiffLine> result = new ArrayList<>();

        if (patch == null || patch.isEmpty()) return result;

        String[] lines = patch.split("\n");

        int newLineNumber = 0;

        for (String line : lines) {

            // 🔹 Parse hunk header
            if (line.startsWith("@@")) {
                String[] parts = line.split(" ");
                String newFilePart = parts[2]; // +10,7

                int startLine = Integer.parseInt(
                        newFilePart.split(",")[0].replace("+", "")
                );

                newLineNumber = startLine - 1;
                continue;
            }

            // ✅ Added line
            if (line.startsWith("+") && !line.startsWith("+++")) {
                newLineNumber++;
                result.add(new DiffLine(newLineNumber, line.substring(1), true));
            }

            // ❌ Removed line → skip
            else if (line.startsWith("-") && !line.startsWith("---")) {
                // do nothing
            }

            // ✅ Context line → increment only
            else {
                newLineNumber++;
            }
        }

        return result;
    }
}