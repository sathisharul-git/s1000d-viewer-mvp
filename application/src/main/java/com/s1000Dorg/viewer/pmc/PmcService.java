package com.s1000Dorg.viewer.pmc;

import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import com.s1000Dorg.viewer.applicability.ApplicabilityDecision;
import com.s1000Dorg.viewer.applicability.ApplicabilityService;
import com.s1000Dorg.viewer.modules.ModuleFiltersResponse;
import com.s1000Dorg.viewer.modules.ModuleService;
import com.s1000Dorg.viewer.csdb.persistence.repository.PmcDmRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.PmcRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PmcService {

    private final PmcRepository pmcRepository;
    private final PmcDmRepository pmcDmRepository;
    private final ModuleService moduleService;
    private final ApplicabilityService applicabilityService;

    public PmcService(
        PmcRepository pmcRepository,
        PmcDmRepository pmcDmRepository,
        ModuleService moduleService,
        ApplicabilityService applicabilityService
    ) {
        this.pmcRepository = pmcRepository;
        this.pmcDmRepository = pmcDmRepository;
        this.moduleService = moduleService;
        this.applicabilityService = applicabilityService;
    }

    public List<PmcListItemResponse> listPmcs() {
        return pmcRepository.findAll().stream()
            .sorted((a, b) -> a.getPmcId().compareToIgnoreCase(b.getPmcId()))
            .map(item -> new PmcListItemResponse(item.getPmcId(), item.getTitle()))
            .toList();
    }

    public PmcModulesResponse modulesForPmc(String pmcId) {
        var pmc = pmcRepository.findByPmcIdIgnoreCase(pmcId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PMC not found"));
        List<String> dmIds = pmcDmRepository.findByPmcIdIgnoreCaseOrderBySortOrder(pmcId).stream()
            .map(rel -> rel.getId().getDmId())
            .toList();
        return new PmcModulesResponse(pmc.getPmcId(), pmc.getTitle(), dmIds);
    }

    public PublicationModulesResponse publicationModules(String pmcId, ApplicabilityContext context) {
        var pmc = pmcRepository.findByPmcIdIgnoreCase(pmcId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PMC not found"));

        List<PublicationModuleItemResponse> modules = new ArrayList<>();
        for (var relation : pmcDmRepository.findByPmcIdIgnoreCaseOrderBySortOrder(pmcId)) {
            moduleService.evaluateModuleRow(relation.getId().getDmId(), context).ifPresent(row -> {
                ApplicabilityDecision decision = ApplicabilityDecision.of(row.dmApplicabilityStatus(), row.dmApplicabilityReason());
                if (!applicabilityService.includeInModuleList(decision)) {
                    return;
                }
                modules.add(new PublicationModuleItemResponse(
                    row.dmId(),
                    row.displayName(),
                    row.systemCode(),
                    row.infoCode(),
                    row.dmApplicabilityStatus(),
                    row.dmApplicabilityDisplayText(),
                    row.dmApplicabilityReason(),
                    row.dmApplicabilitySource(),
                    row.applicability(),
                    row.source(),
                    row.hasPublishedPreview()
                ));
            });
        }

        return new PublicationModulesResponse(
            pmc.getPmcId(),
            pmc.getTitle(),
            new ModuleFiltersResponse(context.aircraft(), context.engine(), context.variant()),
            modules
        );
    }
}
