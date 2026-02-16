package com.s1000Dorg.viewer.admin;

import com.s1000Dorg.viewer.auth.DemoUserStore;
import com.s1000Dorg.viewer.csdb.index.CsdbIndexer;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DemoUserStore demoUserStore;
    private final CsdbIndexer csdbIndexer;

    public AdminController(DemoUserStore demoUserStore, CsdbIndexer csdbIndexer) {
        this.demoUserStore = demoUserStore;
        this.csdbIndexer = csdbIndexer;
    }

    @GetMapping("/users")
    public List<UserSummaryResponse> users() {
        return demoUserStore.findAll().stream()
            .map(user -> new UserSummaryResponse(user.username(), Set.copyOf(user.roles())))
            .toList();
    }

    @PostMapping("/csdb/reindex")
    public String reindexCsdb() {
        csdbIndexer.indexAll();
        return "CSDB reindex completed";
    }
}
