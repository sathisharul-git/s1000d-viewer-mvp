package com.example.s1000dviewer.applicability;

import java.util.List;

public sealed interface ApplicabilityExpression
    permits ApplicabilityExpression.AllOf, ApplicabilityExpression.AnyOf, ApplicabilityExpression.DimensionEquals, ApplicabilityExpression.Unknown {

    record AllOf(List<ApplicabilityExpression> expressions) implements ApplicabilityExpression {
    }

    record AnyOf(List<ApplicabilityExpression> expressions) implements ApplicabilityExpression {
    }

    record DimensionEquals(String dimension, String value) implements ApplicabilityExpression {
    }

    record Unknown(String rawExpression) implements ApplicabilityExpression {
    }
}
