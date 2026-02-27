package com.s1000Dorg.viewer.pmc;

import com.s1000Dorg.viewer.domain.Applicability;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;

public record PublicationModuleItemResponse(
    String dmId,
    String displayName,
    String systemCode,
    String infoCode,
    ApplicabilityResult dmApplicabilityStatus,
    String dmApplicabilityDisplayText,
    String dmApplicabilityReason,
    String dmApplicabilitySource,
    Applicability applicability,
    String source,
    boolean hasPublishedPreview
) {
}
