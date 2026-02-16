package com.s1000Dorg.viewer.modules;

import java.util.List;

public record ModuleListResponse(ModuleFiltersResponse filters, List<ModuleListItemResponse> modules) {
}

