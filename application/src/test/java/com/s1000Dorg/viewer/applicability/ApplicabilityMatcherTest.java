package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.config.ApplicabilityProperties;
import com.s1000Dorg.viewer.domain.Applicability;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicabilityMatcherTest {

    private final ApplicabilityMatcher matcher = new ApplicabilityMatcher(new ApplicabilityProperties());

    @Test
    void returnsApplicableWhenAllRequestedDimensionsMatch() {
        Applicability applicability = new Applicability(
            List.of("A320", "A321"),
            List.of("CFM56"),
            List.of("MOD-12")
        );

        ApplicabilityDecision decision = matcher.evaluate(
            applicability,
            ApplicabilityContext.of("A320", "CFM56", "MOD-12")
        );

        assertThat(decision.result()).isEqualTo(ApplicabilityResult.APPLICABLE);
        assertThat(decision.reason()).contains("aircraft matched: A320");
        assertThat(decision.reason()).contains("engine matched: CFM56");
        assertThat(decision.reason()).contains("variant matched: MOD-12");
    }

    @Test
    void returnsNotApplicableWhenAnyRequestedDimensionMismatches() {
        Applicability applicability = new Applicability(
            List.of("A320"),
            List.of("CFM56"),
            List.of("MOD-12")
        );

        ApplicabilityDecision decision = matcher.evaluate(
            applicability,
            ApplicabilityContext.of("A320", "LEAP-1A", "MOD-12")
        );

        assertThat(decision.result()).isEqualTo(ApplicabilityResult.NOT_APPLICABLE);
        assertThat(decision.reason()).contains("engine mismatch: requested LEAP-1A not in [CFM56]");
        assertThat(matcher.includeInModuleList(decision)).isFalse();
    }

    @Test
    void returnsUnknownWhenRequestedDimensionHasNoDmValues() {
        Applicability applicability = new Applicability(
            List.of("A320"),
            List.of(),
            List.of("MOD-12")
        );

        ApplicabilityDecision decision = matcher.evaluate(
            applicability,
            ApplicabilityContext.of("A320", "CFM56", "MOD-12")
        );

        assertThat(decision.result()).isEqualTo(ApplicabilityResult.UNKNOWN);
        assertThat(decision.reason()).contains("engine unknown: requested CFM56 but DM has no values");
        assertThat(matcher.includeInModuleList(decision)).isTrue();
    }
}


