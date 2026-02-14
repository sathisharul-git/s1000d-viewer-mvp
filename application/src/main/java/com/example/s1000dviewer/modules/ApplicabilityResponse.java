package com.example.s1000dviewer.modules;

public record ApplicabilityResponse(
    java.util.List<String> aircraft,
    java.util.List<String> engine,
    java.util.List<String> variant
) {
}
