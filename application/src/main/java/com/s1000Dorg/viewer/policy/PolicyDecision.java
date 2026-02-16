package com.s1000Dorg.viewer.policy;

public record PolicyDecision(Outcome outcome, String reason) {

    public static PolicyDecision allow(String reason) {
        return new PolicyDecision(Outcome.ALLOW, normalize(reason, "allowed"));
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(Outcome.DENY, normalize(reason, "denied"));
    }

    private static String normalize(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    public enum Outcome {
        ALLOW,
        DENY
    }
}

