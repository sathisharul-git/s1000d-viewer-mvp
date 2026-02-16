package com.s1000Dorg.viewer.auth;

import java.util.Set;

public record DemoUser(String username, String passwordHash, Set<String> roles) {
}
