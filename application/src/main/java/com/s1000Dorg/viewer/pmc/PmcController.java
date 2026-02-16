package com.s1000Dorg.viewer.pmc;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pmc")
public class PmcController {

    private final PmcService pmcService;

    public PmcController(PmcService pmcService) {
        this.pmcService = pmcService;
    }

    @GetMapping
    public List<PmcListItemResponse> listPmcs() {
        return pmcService.listPmcs();
    }

    @GetMapping("/{pmcId}/modules")
    public PmcModulesResponse modulesForPmc(@PathVariable String pmcId) {
        return pmcService.modulesForPmc(pmcId);
    }
}
