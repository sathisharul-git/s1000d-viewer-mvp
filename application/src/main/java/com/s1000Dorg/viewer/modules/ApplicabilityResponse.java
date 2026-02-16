package com.s1000Dorg.viewer.modules;

public record ApplicabilityResponse(
    java.util.List<String> aircraft,
    java.util.List<String> engine,
    java.util.List<String> variant
) {
}

