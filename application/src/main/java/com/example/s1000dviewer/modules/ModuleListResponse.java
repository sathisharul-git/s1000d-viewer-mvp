package com.example.s1000dviewer.modules;

import java.util.List;

public record ModuleListResponse(ModuleFiltersResponse filters, List<ModuleListItemResponse> modules) {
}
