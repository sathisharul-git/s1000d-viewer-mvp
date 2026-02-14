package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.Applicability;
import com.example.s1000dviewer.domain.ApplicabilityResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApplicabilityMatcher {

    public ApplicabilityDecision evaluate(Applicability applicability, ApplicabilityContext context) {
        DimensionDecision aircraft = evaluateDimension("aircraft", context.aircraft(), applicability == null ? List.of() : applicability.aircraft());
        DimensionDecision engine = evaluateDimension("engine", context.engine(), applicability == null ? List.of() : applicability.engine());
        DimensionDecision variant = evaluateDimension("variant", context.variant(), applicability == null ? List.of() : applicability.variant());

        List<String> reasons = new ArrayList<>();
        if (!aircraft.reason().isBlank()) {
            reasons.add(aircraft.reason());
        }
        if (!engine.reason().isBlank()) {
            reasons.add(engine.reason());
        }
        if (!variant.reason().isBlank()) {
            reasons.add(variant.reason());
        }

        ApplicabilityResult overall;
        if (aircraft.result() == ApplicabilityResult.NOT_APPLICABLE
            || engine.result() == ApplicabilityResult.NOT_APPLICABLE
            || variant.result() == ApplicabilityResult.NOT_APPLICABLE) {
            overall = ApplicabilityResult.NOT_APPLICABLE;
        } else if (allProvidedFieldsApplicable(context, aircraft, engine, variant)) {
            overall = ApplicabilityResult.APPLICABLE;
        } else {
            overall = ApplicabilityResult.UNKNOWN;
        }

        return ApplicabilityDecision.of(overall, String.join("; ", reasons));
    }

    public boolean includeInModuleList(ApplicabilityDecision decision) {
        return decision.result() != ApplicabilityResult.NOT_APPLICABLE;
    }

    private boolean allProvidedFieldsApplicable(
        ApplicabilityContext context,
        DimensionDecision aircraft,
        DimensionDecision engine,
        DimensionDecision variant
    ) {
        if (context.aircraft() != null && aircraft.result() != ApplicabilityResult.APPLICABLE) {
            return false;
        }
        if (context.engine() != null && engine.result() != ApplicabilityResult.APPLICABLE) {
            return false;
        }
        if (context.variant() != null && variant.result() != ApplicabilityResult.APPLICABLE) {
            return false;
        }
        return true;
    }

    private DimensionDecision evaluateDimension(String key, String requested, List<String> values) {
        if (requested == null) {
            return new DimensionDecision(ApplicabilityResult.APPLICABLE, "");
        }
        if (values == null || values.isEmpty()) {
            return new DimensionDecision(ApplicabilityResult.UNKNOWN, key + " unknown: requested " + requested + " but DM has no values");
        }

        for (String candidate : values) {
            if (candidate != null && candidate.equalsIgnoreCase(requested)) {
                return new DimensionDecision(ApplicabilityResult.APPLICABLE, key + " matched: " + requested);
            }
        }
        return new DimensionDecision(
            ApplicabilityResult.NOT_APPLICABLE,
            key + " mismatch: requested " + requested + " not in " + values
        );
    }

    private record DimensionDecision(ApplicabilityResult result, String reason) {
    }
}

