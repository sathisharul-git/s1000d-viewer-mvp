package com.s1000Dorg.viewer.modules;

public record ModuleRenderResponse(
    String dmId,
    String source,
    String html,
    ModuleRenderMetaResponse meta,
    ModuleAssetsResponse assets,
    ModuleLinksResponse links
) {
}

