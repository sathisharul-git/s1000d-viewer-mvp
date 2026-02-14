package com.example.s1000dviewer.adapters.fs;

import com.example.s1000dviewer.domain.Applicability;
import java.util.List;

public record PublishedManifestEntry(
    String dmId,
    String title,
    Applicability applicability,
    List<String> icns,
    List<String> dmRefs
) {
}
