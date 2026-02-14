package com.example.s1000dviewer.applicability;

import org.springframework.stereotype.Component;

@Component
public class NoOpBrexValidator implements BrexValidator {

    @Override
    public boolean validate(String dmId, String xmlPayload) {
        // TODO Phase 3: BREX rule validation hook.
        return true;
    }
}
