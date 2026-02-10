package com.example.s1000dviewer.graphics;

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