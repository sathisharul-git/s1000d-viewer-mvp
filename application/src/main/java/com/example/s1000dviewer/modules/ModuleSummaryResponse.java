package com.example.s1000dviewer.modules;

public record ModuleSummaryResponse(
    String dmId,
    String title,
    String aircraft,
    String engine,
    String icnId,
    String fileName
) {
}