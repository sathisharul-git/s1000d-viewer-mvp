package com.s1000Dorg.viewer.graphics;

public record Hotspot(
    String id,
    double x,
    double y,
    double w,
    double h,
    String label,
    String targetDmId
) {
}
