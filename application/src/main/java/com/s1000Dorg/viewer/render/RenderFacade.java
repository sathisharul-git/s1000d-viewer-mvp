package com.s1000Dorg.viewer.render;

import com.s1000Dorg.viewer.config.RenderProperties;
import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import com.s1000Dorg.viewer.domain.DataModuleDescriptor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RenderFacade {

    private final PublishedRenderService publishedRenderService;
    private final QuickRenderService quickRenderService;
    private final RenderProperties renderProperties;

    public RenderFacade(
        PublishedRenderService publishedRenderService,
        QuickRenderService quickRenderService,
        RenderProperties renderProperties
    ) {
        this.publishedRenderService = publishedRenderService;
        this.quickRenderService = quickRenderService;
        this.renderProperties = renderProperties;
    }

    public RenderedDm render(
        DataModuleDescriptor descriptor,
        ApplicabilityResult applicabilityResult,
        ApplicabilityContext context
    ) {
        if (renderProperties.isPublishedPreferred()) {
            return publishedRenderService.render(descriptor.dmId(), descriptor, applicabilityResult)
                .orElseGet(() -> renderQuickOrFail(descriptor, applicabilityResult, context));
        }

        if (renderProperties.isQuickPreviewEnabled()) {
            return quickRenderService.render(descriptor.dmId(), descriptor, applicabilityResult, context);
        }

        return publishedRenderService.render(descriptor.dmId(), descriptor, applicabilityResult)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No render source available"));
    }

    private RenderedDm renderQuickOrFail(
        DataModuleDescriptor descriptor,
        ApplicabilityResult applicabilityResult,
        ApplicabilityContext context
    ) {
        if (!renderProperties.isQuickPreviewEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quick preview is disabled");
        }
        return quickRenderService.render(descriptor.dmId(), descriptor, applicabilityResult, context);
    }
}

