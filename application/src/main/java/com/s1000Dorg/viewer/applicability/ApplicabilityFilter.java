package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.domain.Applicability;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApplicabilityFilter {

    public ApplicabilityResult evaluate(Applicability applicability, ApplicabilityContext context) {
        if (applicability == null || applicability.isUnknown()) {
            return ApplicabilityResult.UNKNOWN;
        }

        boolean unknown = false;
        ApplicabilityResult aircraftResult = evaluateDimension(context.aircraft(), applicability.aircraft());
        ApplicabilityResult engineResult = evaluateDimension(context.engine(), applicability.engine());

        if (aircraftResult == ApplicabilityResult.NOT_APPLICABLE || engineResult == ApplicabilityResult.NOT_APPLICABLE) {
            return ApplicabilityResult.NOT_APPLICABLE;
        }

        if (aircraftResult == ApplicabilityResult.UNKNOWN || engineResult == ApplicabilityResult.UNKNOWN) {
            unknown = true;
        }

        return unknown ? ApplicabilityResult.UNKNOWN : ApplicabilityResult.APPLICABLE;
    }

    public boolean includeInList(ApplicabilityResult result) {
        return result != ApplicabilityResult.NOT_APPLICABLE;
    }

    private ApplicabilityResult evaluateDimension(String filterValue, List<String> values) {
        if (filterValue == null) {
            return ApplicabilityResult.APPLICABLE;
        }
        if (values == null || values.isEmpty()) {
            return ApplicabilityResult.UNKNOWN;
        }

        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(filterValue)) {
                return ApplicabilityResult.APPLICABLE;
            }
        }
        return ApplicabilityResult.NOT_APPLICABLE;
    }
}

