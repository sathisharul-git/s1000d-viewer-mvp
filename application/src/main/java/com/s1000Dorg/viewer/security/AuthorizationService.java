package com.s1000Dorg.viewer.security;

import com.s1000Dorg.viewer.config.OpaProperties;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service("authorizationService")
public class AuthorizationService {

    private static final Set<String> VIEW_ACTIONS = Set.of(
        "VIEW_MODULE_LIST",
        "VIEW_DM",
        "VIEW_GRAPHIC",
        "VIEW_PMC"
    );

    private final OpaClient opaClient;
    private final OpaProperties opaProperties;
    private final Environment environment;

    public AuthorizationService(
        OpaClient opaClient,
        OpaProperties opaProperties,
        Environment environment
    ) {
        this.opaClient = opaClient;
        this.opaProperties = opaProperties;
        this.environment = environment;
    }

    public boolean canViewModuleList(Authentication authentication) {
        return isAllowed(authentication, "VIEW_MODULE_LIST", Map.of(), Map.of());
    }

    public boolean canViewDm(
        Authentication authentication,
        String dmId,
        String aircraft,
        String engine,
        String variant
    ) {
        return isAllowed(
            authentication,
            "VIEW_DM",
            mapOf("dmId", dmId),
            Map.of("applicability", mapOf("aircraft", aircraft, "engine", engine, "variant", variant))
        );
    }

    public boolean canUploadModule(Authentication authentication) {
        return isAllowed(authentication, "UPLOAD_MODULE", Map.of(), Map.of());
    }

    public boolean canViewGraphic(Authentication authentication, String icnId) {
        return isAllowed(authentication, "VIEW_GRAPHIC", mapOf("icnId", icnId), Map.of());
    }

    public boolean canViewPmc(Authentication authentication, String pmcId) {
        return isAllowed(authentication, "VIEW_PMC", mapOf("pmcId", pmcId), Map.of());
    }

    public boolean canReindex(Authentication authentication) {
        return isAllowed(authentication, "REINDEX", Map.of(), Map.of());
    }

    public boolean canViewAdminUsers(Authentication authentication) {
        return isAllowed(authentication, "MANAGE_USERS", Map.of(), Map.of());
    }

    public boolean isAllowed(
        Authentication authentication,
        String action,
        Map<String, Object> resource,
        Map<String, Object> context
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        Set<String> normalizedRoles = canonicalizeRoles(roles);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("user", Map.of("name", authentication.getName(), "roles", normalizedRoles));
        input.put("action", action);
        input.put("resource", sanitize(resource));
        input.put("context", sanitize(context));

        if (!opaProperties.isEnabled()) {
            return roleFallback(action, normalizedRoles);
        }

        Optional<Boolean> decision = opaClient.evaluate(input);
        if (decision.isPresent()) {
            return decision.get();
        }

        if (isDevAuthProfileActive()) {
            return roleFallback(action, normalizedRoles);
        }
        if (opaProperties.isAllowReadOnError() && VIEW_ACTIONS.contains(action)) {
            return roleFallback(action, normalizedRoles);
        }
        return false;
    }

    private boolean roleFallback(String action, Set<String> roles) {
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isEngineer = roles.contains("ROLE_ENGINEER");
        boolean isViewer = roles.contains("ROLE_VIEWER");

        if (VIEW_ACTIONS.contains(action)) {
            return isViewer || isEngineer || isAdmin;
        }
        if ("UPLOAD_MODULE".equals(action)) {
            return isEngineer || isAdmin;
        }
        if ("REINDEX".equals(action) || "MANAGE_USERS".equals(action)) {
            return isAdmin;
        }
        return false;
    }

    private Set<String> canonicalizeRoles(Set<String> roles) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String upper = role.toUpperCase();
            if (upper.contains("ADMIN")) {
                normalized.add("ROLE_ADMIN");
                continue;
            }
            if (upper.contains("ENGINEER")) {
                normalized.add("ROLE_ENGINEER");
                continue;
            }
            if (upper.contains("VIEWER")) {
                normalized.add("ROLE_VIEWER");
                continue;
            }
            normalized.add(role);
        }
        return normalized;
    }

    private Map<String, Object> sanitize(Map<String, Object> values) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof String str && str.isBlank()) {
                continue;
            }
            cleaned.put(entry.getKey(), value);
        }
        return cleaned;
    }

    private Map<String, Object> mapOf(String key, String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return Map.of(key, value);
    }

    private Map<String, Object> mapOf(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (v1 != null && !v1.isBlank()) {
            values.put(k1, v1);
        }
        if (v2 != null && !v2.isBlank()) {
            values.put(k2, v2);
        }
        if (v3 != null && !v3.isBlank()) {
            values.put(k3, v3);
        }
        return values;
    }

    private boolean isDevAuthProfileActive() {
        return Set.of(environment.getActiveProfiles()).contains("dev-auth");
    }
}
