package com.example.s1000dviewer.applicability.eval;

import com.example.s1000dviewer.applicability.ApplicabilityContext;

public interface ApplicabilityEvaluator {
    boolean isApplicable(String expression, ApplicabilityContext context);
}
