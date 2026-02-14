package com.s1000Dorg.viewer.applicability.eval;

import com.s1000Dorg.viewer.applicability.ApplicabilityContext;

public interface ApplicabilityEvaluator {
    boolean isApplicable(String expression, ApplicabilityContext context);
}

