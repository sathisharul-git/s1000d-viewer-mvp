package com.s1000Dorg.viewer.auth;

import java.util.Set;

public record LoginResponse(String token, String username, Set<String> roles) {
}
