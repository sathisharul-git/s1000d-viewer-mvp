package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.Applicability;

public interface ApplicabilityProvider {
    Applicability resolve(String dmId);
}
