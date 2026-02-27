package com.s1000Dorg.viewer.applicability.xml;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;

public class ApplicabilityXmlEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(ApplicabilityXmlEvaluator.class);
    private static final Pattern PREFIXED_NUMERIC = Pattern.compile("^(.*?)(\\d+)$");

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
        if (requiredValues == null || requiredValues.isBlank()) {
            return ApplicabilityResult.UNKNOWN;
        }

        List<String> tokens = Arrays.stream(requiredValues.split("[|,]"))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .collect(Collectors.toList());
        if (tokens.isEmpty()) {
            return ApplicabilityResult.UNKNOWN;
        }

        boolean hasUnknown = false;
        for (String token : tokens) {
            ApplicabilityResult result = evaluateToken(contextValue, token);
            if (result == ApplicabilityResult.APPLICABLE) {
                return ApplicabilityResult.APPLICABLE;
            }
            if (result == ApplicabilityResult.UNKNOWN) {
                hasUnknown = true;
            }
        }
        return hasUnknown ? ApplicabilityResult.UNKNOWN : ApplicabilityResult.NOT_APPLICABLE;
    }

    private ApplicabilityResult evaluateToken(String contextValue, String token) {
        if (token.contains("~")) {
            return checkRange(contextValue, token);
        }
        String normalizedContext = normalizeBoolean(contextValue);
        String normalizedToken = normalizeBoolean(token);
        return normalizedToken.equalsIgnoreCase(normalizedContext)
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

            String minToken = parts[0].trim();
            String maxToken = parts[1].trim();

            // Numeric range
            if (isNumeric(minToken) && isNumeric(maxToken) && isNumeric(contextValue)) {
                double min = Double.parseDouble(minToken);
                double max = Double.parseDouble(maxToken);
                double value = Double.parseDouble(contextValue);
                return (value >= min && value <= max)
                    ? ApplicabilityResult.APPLICABLE
                    : ApplicabilityResult.NOT_APPLICABLE;
            }

            // Prefixed numeric range: POST-001~POST-999
            PrefixValue min = toPrefixValue(minToken);
            PrefixValue max = toPrefixValue(maxToken);
            PrefixValue ctx = toPrefixValue(contextValue);
            if (min == null || max == null || ctx == null) {
                logger.warn("Unsupported range token '{}'", range);
                return ApplicabilityResult.UNKNOWN;
            }

            String rangePrefix = min.prefix();
            if (!max.prefix().equalsIgnoreCase(rangePrefix) || !ctx.prefix().equalsIgnoreCase(rangePrefix)) {
                return ApplicabilityResult.NOT_APPLICABLE;
            }
            return (ctx.value() >= min.value() && ctx.value() <= max.value())
                ? ApplicabilityResult.APPLICABLE
                : ApplicabilityResult.NOT_APPLICABLE;
        } catch (NumberFormatException e) {
            logger.warn("Could not parse numeric range '{}' or value '{}'", range, contextValue, e);
            return ApplicabilityResult.UNKNOWN;
        }
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private PrefixValue toPrefixValue(String token) {
        if (token == null) {
            return null;
        }
        Matcher matcher = PREFIXED_NUMERIC.matcher(token.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            String prefix = matcher.group(1).trim();
            int value = Integer.parseInt(matcher.group(2));
            return new PrefixValue(prefix, value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeBoolean(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase("yes") || normalized.equalsIgnoreCase("y") || normalized.equals("1")) {
            return "true";
        }
        if (normalized.equalsIgnoreCase("no") || normalized.equalsIgnoreCase("n") || normalized.equals("0")) {
            return "false";
        }
        return normalized;
    }

    private record PrefixValue(String prefix, int value) {
    }
}
