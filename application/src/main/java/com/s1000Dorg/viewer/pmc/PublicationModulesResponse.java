package com.s1000Dorg.viewer.pmc;

import com.s1000Dorg.viewer.modules.ModuleFiltersResponse;
import java.util.List;

public record PublicationModulesResponse(
    String pmcId,
    String title,
    ModuleFiltersResponse filters,
    List<PublicationModuleItemResponse> modules
) {
}
