package com.s1000Dorg.viewer.pmc;

import com.s1000Dorg.viewer.applicability.ApplicabilityContextFactory;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publications")
public class PublicationController {

    private final PmcService pmcService;
    private final ApplicabilityContextFactory applicabilityContextFactory;

    public PublicationController(PmcService pmcService, ApplicabilityContextFactory applicabilityContextFactory) {
        this.pmcService = pmcService;
        this.applicabilityContextFactory = applicabilityContextFactory;
    }

    @GetMapping("/{pmcId}/modules")
    @PreAuthorize("@authorizationService.canViewPmc(authentication, #pmcId)")
    public PublicationModulesResponse publicationModules(
        @PathVariable String pmcId,
        @RequestParam(required = false) String aircraft,
        @RequestParam(required = false) String engine,
        @RequestParam(required = false) String variant,
        @RequestParam Map<String, String> requestParams
    ) {
        return pmcService.publicationModules(
            pmcId,
            applicabilityContextFactory.fromRequest(aircraft, engine, variant, requestParams)
        );
    }
}
