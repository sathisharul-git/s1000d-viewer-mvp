package com.s1000Dorg.viewer.render;

import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.adapters.fs.PublishedManifestEntry;
import com.s1000Dorg.viewer.domain.Applicability;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import com.s1000Dorg.viewer.domain.DataModuleDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PublishedRenderService {

    private static final Pattern DM_REF_PATTERN = Pattern.compile("DMC-[A-Z0-9_-]+");
    private static final Pattern ICN_PATTERN = Pattern.compile("ICN-[A-Z0-9_-]+");

    private final FsDataRepository repository;

    public PublishedRenderService(FsDataRepository repository) {
        this.repository = repository;
    }

    public Optional<RenderedDm> render(String dmId, DataModuleDescriptor descriptor, ApplicabilityResult applicabilityResult) {
        Optional<String> html = repository.readPublishedHtml(dmId);
        if (html.isEmpty()) {
            return Optional.empty();
        }

        Map<String, PublishedManifestEntry> manifest = repository.readPublishedManifest();
        PublishedManifestEntry entry = manifest.get(dmId.toUpperCase(Locale.ROOT));

        List<String> icns = entry != null && entry.icns() != null && !entry.icns().isEmpty()
            ? entry.icns()
            : extract(ICN_PATTERN, html.get());
        List<String> dmRefs = entry != null && entry.dmRefs() != null && !entry.dmRefs().isEmpty()
            ? entry.dmRefs()
            : extract(DM_REF_PATTERN, html.get().toUpperCase(Locale.ROOT));

        String title = entry != null && entry.title() != null && !entry.title().isBlank() ? entry.title() : descriptor.title();
        Applicability applicability = descriptor.applicability();

        return Optional.of(new RenderedDm(
            dmId,
            "published",
            html.get(),
            title,
            applicability,
            applicabilityResult,
            icns,
            dmRefs
        ));
    }

    private List<String> extract(Pattern pattern, String input) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String value = matcher.group();
            if (!values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }
}

