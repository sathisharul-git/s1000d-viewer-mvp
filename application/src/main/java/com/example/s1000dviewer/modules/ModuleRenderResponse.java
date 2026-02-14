package com.example.s1000dviewer.modules;

public record ModuleRenderResponse(
    String dmId,
    String source,
    String html,
    ModuleRenderMetaResponse meta,
    ModuleAssetsResponse assets,
    ModuleLinksResponse links
) {
}
