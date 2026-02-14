package com.example.s1000dviewer.policy;

import org.springframework.stereotype.Component;

@Component
public class NoOpBrexValidator implements BrexValidator {

    @Override
    public boolean validate(String dmId, String xmlPayload) {
        // TODO: BREX rule validation hook.
        return true;
    }
}
