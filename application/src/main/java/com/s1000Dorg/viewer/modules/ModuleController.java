package com.s1000Dorg.viewer.modules;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {

    private final ModuleService moduleService;

    public ModuleController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.canViewModuleList(authentication)")
    public ModuleListResponse listModules(
        @RequestParam(required = false) String aircraft,
        @RequestParam(required = false) String engine,
        @RequestParam(required = false) String variant
    ) {
        return moduleService.listModules(aircraft, engine, variant);
    }

    @GetMapping("/{dmId}/render")
    @PreAuthorize("@authorizationService.canViewDm(authentication, #dmId, #aircraft, #engine, #variant)")
    public ModuleRenderResponse renderModule(
        @PathVariable String dmId,
        @RequestParam(required = false) String aircraft,
        @RequestParam(required = false) String engine,
        @RequestParam(required = false) String variant
    ) {
        return moduleService.renderModule(dmId, aircraft, engine, variant);
    }

    @PostMapping("/upload")
    @PreAuthorize("@authorizationService.canUploadModule(authentication)")
    public ModuleUploadResponse uploadModule(
        @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) String aircraft,
        @RequestParam(required = false) String engine,
        @RequestParam(required = false) String variant,
        @RequestParam(required = false) String title,
        @RequestParam(required = false) String icnId
    ) {
        return moduleService.uploadModule(file, aircraft, engine, variant, title, icnId);
    }
}

