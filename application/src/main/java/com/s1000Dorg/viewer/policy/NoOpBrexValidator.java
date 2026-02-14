package com.s1000Dorg.viewer.policy;

import org.springframework.stereotype.Component;

@Component
public class NoOpBrexValidator implements BrexValidator {

    @Override
    public boolean validate(String dmId, String xmlPayload) {
        // TODO: BREX rule validation hook.
        return true;
    }
}

