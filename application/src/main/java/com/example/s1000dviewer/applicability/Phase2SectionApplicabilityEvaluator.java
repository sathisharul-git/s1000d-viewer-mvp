package com.example.s1000dviewer.applicability;

import org.springframework.stereotype.Component;

@Component
public class Phase2SectionApplicabilityEvaluator implements ApplicabilityEvaluator {

    private final ApplicabilityExpressionParser expressionParser;

    public Phase2SectionApplicabilityEvaluator(ApplicabilityExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
    }

    @Override
    public boolean isApplicable(String expression, ApplicabilityContext context) {
        ApplicabilityExpression ast = expressionParser.parse(expression);
        // TODO Phase 2: evaluate AST against context and remove/mark non-applicable sections.
        if (ast instanceof ApplicabilityExpression.Unknown) {
            return true;
        }
        return true;
    }
}
