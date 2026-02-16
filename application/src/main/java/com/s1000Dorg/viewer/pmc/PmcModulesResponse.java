package com.s1000Dorg.viewer.pmc;

import java.util.List;

public record PmcModulesResponse(
    String pmcId,
    String title,
    List<String> dmIds
) {
}
