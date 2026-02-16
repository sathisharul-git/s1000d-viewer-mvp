package com.s1000Dorg.viewer.auth;

import com.s1000Dorg.viewer.config.SecurityProperties;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("dev-auth")
public class DemoUserStore {

    private final Map<String, DemoUser> users = new LinkedHashMap<>();

    public DemoUserStore(PasswordEncoder passwordEncoder, SecurityProperties securityProperties) {
        for (SecurityProperties.DemoUserConfig configuredUser : securityProperties.getDemoUsers()) {
            String username = configuredUser.getUsername();
            String password = configuredUser.getPassword();
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                continue;
            }
            users.put(
                username,
                new DemoUser(
                    username,
                    passwordEncoder.encode(password),
                    configuredUser.getRoles() == null ? java.util.Set.of() : java.util.Set.copyOf(configuredUser.getRoles())
                )
            );
        }
    }

    public Optional<DemoUser> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    public Collection<DemoUser> findAll() {
        return users.values();
    }
}
