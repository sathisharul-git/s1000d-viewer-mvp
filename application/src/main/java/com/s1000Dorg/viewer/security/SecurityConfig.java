package com.s1000Dorg.viewer.security;

import com.s1000Dorg.viewer.auth.DemoUser;
import com.s1000Dorg.viewer.auth.DemoUserStore;
import com.s1000Dorg.viewer.config.LdapProperties;
import com.s1000Dorg.viewer.config.SecurityProperties;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.ldap.LdapBindAuthenticationManagerFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties securityProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(securityProperties.getCors().getAllowedOrigins());
        config.setAllowedMethods(securityProperties.getCors().getAllowedMethods());
        config.setAllowedHeaders(securityProperties.getCors().getAllowedHeaders());
        config.setExposedHeaders(securityProperties.getCors().getExposedHeaders());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Profile("dev-auth")
    public UserDetailsService userDetailsService(DemoUserStore demoUserStore) {
        return username -> demoUserStore.findByUsername(username)
            .map(this::toUserDetails)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    @Profile("dev-auth")
    public AuthenticationManager devAuthenticationManager(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    @Profile("!dev-auth")
    public BaseLdapPathContextSource applicationLdapContextSource(LdapProperties ldapProperties) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapProperties.getUrl());
        contextSource.setBase(ldapProperties.getBaseDn());
        if (ldapProperties.getManagerDn() != null && !ldapProperties.getManagerDn().isBlank()) {
            contextSource.setUserDn(ldapProperties.getManagerDn());
            contextSource.setPassword(ldapProperties.getManagerPassword());
        }
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    @Bean
    @Profile("!dev-auth")
    public AuthenticationManager ldapAuthenticationManager(
        @Qualifier("applicationLdapContextSource") BaseLdapPathContextSource contextSource,
        LdapProperties ldapProperties,
        GrantedAuthoritiesMapper ldapAuthoritiesMapper
    ) {
        LdapBindAuthenticationManagerFactory factory = new LdapBindAuthenticationManagerFactory(contextSource);
        factory.setUserDnPatterns(ldapProperties.userDnPatterns());

        DefaultLdapAuthoritiesPopulator authoritiesPopulator =
            new DefaultLdapAuthoritiesPopulator(contextSource, ldapProperties.getGroupSearchBase());
        authoritiesPopulator.setSearchSubtree(true);
        authoritiesPopulator.setGroupSearchFilter(ldapProperties.getGroupSearchFilter());
        factory.setLdapAuthoritiesPopulator(authoritiesPopulator);
        factory.setAuthoritiesMapper(ldapAuthoritiesMapper);
        return factory.createAuthenticationManager();
    }

    @Bean
    public GrantedAuthoritiesMapper ldapAuthoritiesMapper() {
        return authorities -> {
            Set<SimpleGrantedAuthority> mapped = new LinkedHashSet<>();
            for (var authority : authorities) {
                String normalized = normalizeAuthority(authority.getAuthority());
                if (normalized != null && !normalized.isBlank()) {
                    mapped.add(new SimpleGrantedAuthority(normalized));
                }
            }
            return mapped;
        };
    }

    private UserDetails toUserDetails(DemoUser user) {
        return User.builder()
            .username(user.username())
            .password(user.passwordHash())
            .authorities(user.roles().stream().map(SimpleGrantedAuthority::new).toList())
            .build();
    }

    private String normalizeAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        if ("ROLE_ADMIN".equals(authority) || "ROLE_ENGINEER".equals(authority) || "ROLE_VIEWER".equals(authority)) {
            return authority;
        }

        String raw = authority;
        if (raw.startsWith("ROLE_")) {
            raw = raw.substring("ROLE_".length());
        }
        String normalized = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.contains("s1000d-admin")) {
            return "ROLE_ADMIN";
        }
        if (normalized.contains("s1000d-engineer")) {
            return "ROLE_ENGINEER";
        }
        if (normalized.contains("s1000d-viewer")) {
            return "ROLE_VIEWER";
        }
        return "ROLE_" + raw.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}

