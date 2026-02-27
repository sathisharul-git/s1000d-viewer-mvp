package com.s1000Dorg.viewer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${s1000d.opa.enabled:false}")
    private boolean opaEnabled;

    /**
     * Standard Security Configuration for Production/LDAP
     */
    @Bean
    @Profile("!dev-auth")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // Standard role-based access as a baseline
                auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                auth.requestMatchers("/api/upload/**").hasAnyRole("ADMIN", "ENGINEER");
                auth.anyRequest().authenticated();
            })
            .httpBasic(Customizer.withDefaults());
        
        return http.build();
    }

    /**
     * Fallback Security Configuration for Local Development (dev-auth profile)
     */
    @Bean
    @Profile("dev-auth")
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}