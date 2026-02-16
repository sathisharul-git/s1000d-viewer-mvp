package com.s1000Dorg.viewer.admin;

import com.s1000Dorg.viewer.csdb.index.CsdbIndexer;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CsdbIndexer csdbIndexer;

    public AdminController(CsdbIndexer csdbIndexer) {
        this.csdbIndexer = csdbIndexer;
    }

    @GetMapping("/users")
    @PreAuthorize("@authorizationService.canViewAdminUsers(authentication)")
    public List<UserSummaryResponse> users(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return List.of(new UserSummaryResponse(authentication.getName(), roles));
    }

    @PostMapping("/csdb/reindex")
    @PreAuthorize("@authorizationService.canReindex(authentication)")
    public String reindexCsdb() {
        csdbIndexer.indexAll();
        return "CSDB reindex completed";
    }

    @PostMapping("/reindex")
    @PreAuthorize("@authorizationService.canReindex(authentication)")
    public String reindex() {
        csdbIndexer.indexAll();
        return "CSDB reindex completed";
    }
}
