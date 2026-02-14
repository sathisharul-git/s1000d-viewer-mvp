package com.s1000Dorg.viewer.auth;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DemoUserStore {

    private final Map<String, DemoUser> users = new LinkedHashMap<>();

    public DemoUserStore(PasswordEncoder passwordEncoder) {
        users.put("admin", new DemoUser("admin", passwordEncoder.encode("admin123"), Set.of("ROLE_ADMIN")));
        users.put("eng", new DemoUser("eng", passwordEncoder.encode("eng123"), Set.of("ROLE_ENGINEER")));
        users.put("view", new DemoUser("view", passwordEncoder.encode("view123"), Set.of("ROLE_VIEWER")));
    }

    public Optional<DemoUser> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    public Collection<DemoUser> findAll() {
        return users.values();
    }
}
