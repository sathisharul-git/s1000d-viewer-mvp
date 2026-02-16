package com.s1000Dorg.viewer.applicability.xml;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;

public class ApplicabilityXmlEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(ApplicabilityXmlEvaluator.class);

    public ApplicabilityResult evaluate(ApplicXmlExpression expression, ApplicabilityContext context) {
        if (expression == null) {
            return ApplicabilityResult.UNKNOWN;
        }

        if (expression instanceof ApplicXmlExpression.Evaluate eval) {
            return evaluateEvaluate(eval, context);
        } else if (expression instanceof ApplicXmlExpression.Assert assertExpr) {
            return evaluateAssert(assertExpr, context);
        } else {
            logger.warn("Unknown expression type: {}", expression.getClass().getName());
            return ApplicabilityResult.UNKNOWN;
        }
    }

    private ApplicabilityResult evaluateEvaluate(ApplicXmlExpression.Evaluate eval, ApplicabilityContext context) {
        boolean isAnd = "and".equalsIgnoreCase(eval.andOr());
        
        if (isAnd) {
            boolean hasUnknown = false;
            for (ApplicXmlExpression expr : eval.expressions()) {
                ApplicabilityResult result = evaluate(expr, context);
                if (result == ApplicabilityResult.NOT_APPLICABLE) {
                    return ApplicabilityResult.NOT_APPLICABLE;
                }
                if (result == ApplicabilityResult.UNKNOWN) {
                    hasUnknown = true;
                }
            }
            return hasUnknown ? ApplicabilityResult.UNKNOWN : ApplicabilityResult.APPLICABLE;
        } else { // OR
            boolean hasUnknown = false;
            for (ApplicXmlExpression expr : eval.expressions()) {
                ApplicabilityResult result = evaluate(expr, context);
                if (result == ApplicabilityResult.APPLICABLE) {
                    return ApplicabilityResult.APPLICABLE;
                }
                if (result == ApplicabilityResult.UNKNOWN) {
                    hasUnknown = true;
                }
            }
            return hasUnknown ? ApplicabilityResult.UNKNOWN : ApplicabilityResult.NOT_APPLICABLE;
        }
    }

    private ApplicabilityResult evaluateAssert(ApplicXmlExpression.Assert assertExpr, ApplicabilityContext context) {
        String key = assertExpr.applicPropertyIdent().toLowerCase();
        String contextValue = context.getProductAttribute(key);

        if (contextValue == null) {
            // If the context does not provide the attribute, we can't determine applicability.
            return ApplicabilityResult.UNKNOWN;
        }

        String requiredValues = assertExpr.applicPropertyValues();

        // Range check
        if (requiredValues.contains("~")) {
            return checkRange(contextValue, requiredValues);
        }

        // IN check (comma-separated values)
        if (requiredValues.contains(",")) {
            List<String> requiredList = Arrays.stream(requiredValues.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
            return requiredList.stream().anyMatch(val -> val.equalsIgnoreCase(contextValue))
                ? ApplicabilityResult.APPLICABLE
                : ApplicabilityResult.NOT_APPLICABLE;
        }

        // Equals check
        return requiredValues.equalsIgnoreCase(contextValue)
            ? ApplicabilityResult.APPLICABLE
            : ApplicabilityResult.NOT_APPLICABLE;
    }


    
    private ApplicabilityResult checkRange(String contextValue, String range) {
        try {
            String[] parts = range.split("~");
            if (parts.length != 2) {
                logger.warn("Invalid range format: {}", range);
                return ApplicabilityResult.UNKNOWN;
            }

            double min = Double.parseDouble(parts[0].trim());
            double max = Double.parseDouble(parts[1].trim());
            double value = Double.parseDouble(contextValue);

            return (value >= min && value <= max)
                ? ApplicabilityResult.APPLICABLE
                : ApplicabilityResult.NOT_APPLICABLE;
        } catch (NumberFormatException e) {
            logger.warn("Could not parse numeric range '{}' or value '{}'", range, contextValue, e);
            return ApplicabilityResult.UNKNOWN;
        }
    }
}
