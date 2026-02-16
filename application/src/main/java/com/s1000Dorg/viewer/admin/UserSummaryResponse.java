package com.s1000Dorg.viewer.admin;

import java.util.Set;

public record UserSummaryResponse(String username, Set<String> roles) {
}
