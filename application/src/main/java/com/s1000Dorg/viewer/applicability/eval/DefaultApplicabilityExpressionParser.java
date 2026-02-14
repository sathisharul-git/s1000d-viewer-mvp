package com.s1000Dorg.viewer.applicability.eval;

import org.springframework.stereotype.Component;

@Component
public class DefaultApplicabilityExpressionParser implements ApplicabilityExpressionParser {

    @Override
    public ApplicabilityExpression parse(String expression) {
        // TODO: parse S1000D applicability markup into a typed AST.
        return new ApplicabilityExpression.Unknown(expression);
    }
}


