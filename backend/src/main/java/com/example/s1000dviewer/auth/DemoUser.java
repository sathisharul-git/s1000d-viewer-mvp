package com.example.s1000dviewer.auth;

import java.util.Set;

public record DemoUser(String username, String passwordHash, Set<String> roles) {
}