package com.s1000Dorg.viewer.adapters.fs;

import com.s1000Dorg.viewer.domain.Applicability;
import java.util.List;

public record PublishedManifestEntry(
    String dmId,
    String title,
    Applicability applicability,
    List<String> icns,
    List<String> dmRefs
) {
}

