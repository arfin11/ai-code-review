package com.arfin.code.review.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileDiff {
    private String filename;
    private String patch;
}