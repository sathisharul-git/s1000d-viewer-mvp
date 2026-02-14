package com.example.s1000dviewer.render;

import com.example.s1000dviewer.domain.ApplicabilityResult;
import com.example.s1000dviewer.domain.DataModuleDescriptor;
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
