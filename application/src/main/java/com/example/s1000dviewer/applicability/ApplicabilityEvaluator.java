package com.example.s1000dviewer.applicability;

public interface ApplicabilityEvaluator {
    boolean isApplicable(String expression, ApplicabilityContext context);
}
