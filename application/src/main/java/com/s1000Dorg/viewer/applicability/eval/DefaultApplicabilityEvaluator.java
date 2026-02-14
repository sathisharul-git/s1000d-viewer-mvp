package com.s1000Dorg.viewer.applicability.eval;

import com.s1000Dorg.viewer.applicability.ApplicabilityContext;

import org.springframework.stereotype.Component;

@Component
public class DefaultApplicabilityEvaluator implements ApplicabilityEvaluator {

    private final ApplicabilityExpressionParser expressionParser;

    public DefaultApplicabilityEvaluator(ApplicabilityExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
    }

    @Override
    public boolean isApplicable(String expression, ApplicabilityContext context) {
        ApplicabilityExpression ast = expressionParser.parse(expression);
        // TODO: evaluate AST against context and remove/mark non-applicable sections.
        if (ast instanceof ApplicabilityExpression.Unknown) {
            return true;
        }
        return true;
    }
}


