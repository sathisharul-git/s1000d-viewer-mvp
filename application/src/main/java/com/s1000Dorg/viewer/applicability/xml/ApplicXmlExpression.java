package com.s1000Dorg.viewer.applicability.xml;

import java.util.List;

public sealed interface ApplicXmlExpression permits ApplicXmlExpression.Evaluate, ApplicXmlExpression.Assert {
    record Evaluate(String andOr, List<ApplicXmlExpression> expressions) implements ApplicXmlExpression {}
    record Assert(String applicPropertyIdent, String applicPropertyValues) implements ApplicXmlExpression {}
}

