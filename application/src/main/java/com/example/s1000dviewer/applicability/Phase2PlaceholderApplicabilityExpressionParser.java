package com.example.s1000dviewer.applicability;

import org.springframework.stereotype.Component;

@Component
public class Phase2PlaceholderApplicabilityExpressionParser implements ApplicabilityExpressionParser {

    @Override
    public ApplicabilityExpression parse(String expression) {
        // TODO Phase 2: parse S1000D applicability markup into a typed AST.
        return new ApplicabilityExpression.Unknown(expression);
    }
}
