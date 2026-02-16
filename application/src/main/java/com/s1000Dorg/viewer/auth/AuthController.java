package com.s1000Dorg.viewer.auth;

import com.s1000Dorg.viewer.security.JwtService;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );

        UserDetails principal = (UserDetails) authentication.getPrincipal();
        Set<String> roles = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        String token = jwtService.generateToken(principal.getUsername(), roles);
        return ResponseEntity.ok(new LoginResponse(token, principal.getUsername(), roles));
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        String token = jwtService.generateToken(authentication.getName(), roles);
        return ResponseEntity.ok(new LoginResponse(token, authentication.getName(), roles));
    }
}
