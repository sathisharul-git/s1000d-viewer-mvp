package com.example.s1000dviewer.applicability;

import org.springframework.stereotype.Component;

@Component
public class Phase2SectionApplicabilityEvaluator implements ApplicabilityEvaluator {

    @Override
    public boolean isApplicable(String expression, ApplicabilityContext context) {
        // TODO Phase 2: parse applicability markup into expression tree and evaluate against context.
        return true;
    }
}
