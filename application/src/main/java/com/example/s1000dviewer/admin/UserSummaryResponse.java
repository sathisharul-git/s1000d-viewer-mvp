package com.example.s1000dviewer.admin;

import java.util.Set;

public record UserSummaryResponse(String username, Set<String> roles) {
}