package com.s1000Dorg.viewer.modules;

public record ModuleRenderResponse(
    String dmId,
    String source,
    String html,
    ModuleApplicabilitySummaryResponse applicability,
    InlineApplicabilityResponse inlineApplicability,
    ModuleRenderMetaResponse meta,
    ModuleAssetsResponse assets,
    ModuleLinksResponse links
) {
}

