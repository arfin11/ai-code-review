package com.arfin.code.review.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewContext {
    private String fileName;
    private String patch;
    private String snippet;
    private List<Integer> changedLines = new ArrayList<>();
    private String fileContent;
}
