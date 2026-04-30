package com.arfin.code.review.model;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DiffLine {
    private int lineNumber;
    private String content;
    private boolean added;
}