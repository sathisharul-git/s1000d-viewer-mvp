package com.s1000Dorg.viewer.pmc;

import com.s1000Dorg.viewer.csdb.persistence.repository.PmcDmRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.PmcRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PmcService {

    private final PmcRepository pmcRepository;
    private final PmcDmRepository pmcDmRepository;

    public PmcService(PmcRepository pmcRepository, PmcDmRepository pmcDmRepository) {
        this.pmcRepository = pmcRepository;
        this.pmcDmRepository = pmcDmRepository;
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
}
