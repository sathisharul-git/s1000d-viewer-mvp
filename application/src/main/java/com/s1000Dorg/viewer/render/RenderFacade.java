package com.s1000Dorg.viewer.render;

import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import com.s1000Dorg.viewer.domain.DataModuleDescriptor;
import org.springframework.stereotype.Service;

@Service
public class RenderFacade {

    private final PublishedRenderService publishedRenderService;
    private final QuickRenderService quickRenderService;

    public RenderFacade(PublishedRenderService publishedRenderService, QuickRenderService quickRenderService) {
        this.publishedRenderService = publishedRenderService;
        this.quickRenderService = quickRenderService;
    }

    public RenderedDm render(DataModuleDescriptor descriptor, ApplicabilityResult applicabilityResult) {
        return publishedRenderService.render(descriptor.dmId(), descriptor, applicabilityResult)
            .orElseGet(() -> quickRenderService.render(descriptor.dmId(), descriptor, applicabilityResult));
    }
}

