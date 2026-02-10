package com.example.s1000dviewer.modules;

import java.util.List;
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
    public List<ModuleSummaryResponse> listModules(
        @RequestParam(required = false) String aircraft,
        @RequestParam(required = false) String engine
    ) {
        return moduleService.listModules(aircraft, engine);
    }

    @GetMapping("/{dmId}")
    public ModuleContentResponse getModule(
        @PathVariable String dmId,
        @RequestParam(required = false) String aircraft,
        @RequestParam(required = false) String engine
    ) {
        return moduleService.getModuleContent(dmId, aircraft, engine);
    }

    @PostMapping("/upload")
    public ModuleUploadResponse uploadModule(
        @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) String aircraft,
        @RequestParam(required = false) String engine,
        @RequestParam(required = false) String title,
        @RequestParam(required = false) String icnId
    ) {
        return moduleService.uploadModule(file, aircraft, engine, title, icnId);
    }
}