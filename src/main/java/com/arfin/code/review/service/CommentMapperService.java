package com.arfin.code.review.service;

import com.arfin.code.review.model.DiffLine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentMapperService {

    public DiffLine mapToLine(List<DiffLine> lines, String hint) {

        if (hint == null) return null;

        for (DiffLine line : lines) {
            if (normalize(line.getContent())
                    .contains(normalize(hint))) {
                return line;
            }
        }
        return null;
    }

    private String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase();
    }
}