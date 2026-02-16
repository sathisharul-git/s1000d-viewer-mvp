package com.s1000Dorg.viewer.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "viewer.security")
public class SecurityProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Oidc oidc = new Oidc();
    private final ClaimMapping claimMapping = new ClaimMapping();
    private List<DemoUserConfig> demoUsers = new ArrayList<>();

    public Jwt getJwt() {
        return jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public Oidc getOidc() {
        return oidc;
    }

    public ClaimMapping getClaimMapping() {
        return claimMapping;
    }

    public List<DemoUserConfig> getDemoUsers() {
        return demoUsers;
    }

    public void setDemoUsers(List<DemoUserConfig> demoUsers) {
        this.demoUsers = demoUsers;
    }

    public static class Jwt {

        private String secret = "";
        private long expirationSeconds = 28800;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationSeconds() {
            return expirationSeconds;
        }

        public void setExpirationSeconds(long expirationSeconds) {
            this.expirationSeconds = expirationSeconds;
        }
    }

    public static class Cors {

        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = List.of("*");
        private List<String> exposedHeaders = List.of("Authorization");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }
    }

    public static class Oidc {

        private boolean enabled = false;
        private String issuerUri = "";
        private String clientId = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }

    public static class ClaimMapping {

        private String rolesClaim = "roles";
        private String usernameClaim = "sub";

        public String getRolesClaim() {
            return rolesClaim;
        }

        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
        }

        public String getUsernameClaim() {
            return usernameClaim;
        }

        public void setUsernameClaim(String usernameClaim) {
            this.usernameClaim = usernameClaim;
        }
    }

    public static class DemoUserConfig {

        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
