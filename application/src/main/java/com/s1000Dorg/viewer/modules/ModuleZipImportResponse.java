package com.s1000Dorg.viewer.modules;

public record ModuleZipImportResponse(
    int importedDmCount,
    int importedPmcCount,
    int importedIcnCount,
    int skippedCount,
    String message
) {
}

