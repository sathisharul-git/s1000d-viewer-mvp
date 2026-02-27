package com.s1000Dorg.viewer.applicability.xml;

import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicabilityXmlEvaluatorTest {

    private final ApplicabilityXmlParser parser = new ApplicabilityXmlParser();
    private final ApplicabilityXmlEvaluator evaluator = new ApplicabilityXmlEvaluator();

    @Test
    void supportsPipeSeparatedLiteralValues() throws Exception {
        String xml = "<applic><evaluate andOr=\"and\"><assert applicPropertyIdent=\"type\" applicPropertyValues=\"A320|A321\"/></evaluate></applic>";
        ApplicXmlExpression expression = parser.parse(xml);

        ApplicabilityResult result = evaluator.evaluate(
            expression,
            ApplicabilityContext.of(null, null, null, Map.of("type", "A321"))
        );

        assertThat(result).isEqualTo(ApplicabilityResult.APPLICABLE);
    }

    @Test
    void supportsNumericRangeValues() throws Exception {
        String xml = "<applic><evaluate andOr=\"and\"><assert applicPropertyIdent=\"serialNo\" applicPropertyValues=\"100~200\"/></evaluate></applic>";
        ApplicXmlExpression expression = parser.parse(xml);

        ApplicabilityResult result = evaluator.evaluate(
            expression,
            ApplicabilityContext.of(null, null, null, Map.of("serialNo", "150"))
        );

        assertThat(result).isEqualTo(ApplicabilityResult.APPLICABLE);
    }

    @Test
    void supportsPrefixedRangeValues() throws Exception {
        String xml = "<applic><evaluate andOr=\"and\"><assert applicPropertyIdent=\"sb\" applicPropertyValues=\"POST-001~POST-999\"/></evaluate></applic>";
        ApplicXmlExpression expression = parser.parse(xml);

        ApplicabilityResult inRange = evaluator.evaluate(
            expression,
            ApplicabilityContext.of(null, null, null, Map.of("sb", "POST-210"))
        );
        ApplicabilityResult outOfRange = evaluator.evaluate(
            expression,
            ApplicabilityContext.of(null, null, null, Map.of("sb", "PRE-210"))
        );

        assertThat(inRange).isEqualTo(ApplicabilityResult.APPLICABLE);
        assertThat(outOfRange).isEqualTo(ApplicabilityResult.NOT_APPLICABLE);
    }

    @Test
    void normalizesBooleanLikeValues() throws Exception {
        String xml = "<applic><evaluate andOr=\"and\"><assert applicPropertyIdent=\"tourFinished\" applicPropertyValues=\"yes\"/></evaluate></applic>";
        ApplicXmlExpression expression = parser.parse(xml);

        ApplicabilityResult result = evaluator.evaluate(
            expression,
            ApplicabilityContext.of(null, null, null, Map.of("tourFinished", "1"))
        );

        assertThat(result).isEqualTo(ApplicabilityResult.APPLICABLE);
    }
}
