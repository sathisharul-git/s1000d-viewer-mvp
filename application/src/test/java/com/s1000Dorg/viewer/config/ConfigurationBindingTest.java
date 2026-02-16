package com.s1000Dorg.viewer.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConfigurationBindingTest {

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private RenderProperties renderProperties;

    @Autowired
    private ApplicabilityProperties applicabilityProperties;

    @Autowired
    private PolicyProperties policyProperties;

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void bindsEnterpriseConfigurationProperties() {
        assertThat(storageProperties.getCsdbRoot()).contains("src/test/resources/test-data/csdb");
        assertThat(storageProperties.getPublishedRoot()).contains("src/test/resources/test-data/published");
        assertThat(renderProperties.isPublishedPreferred()).isTrue();
        assertThat(renderProperties.isQuickPreviewEnabled()).isTrue();
        assertThat(applicabilityProperties.getUnknownPolicy()).isEqualTo(ApplicabilityProperties.UnknownPolicy.INCLUDE);
        assertThat(applicabilityProperties.getAllowedDimensions()).contains("aircraft", "engine", "variant");
        assertThat(applicabilityProperties.getFragmentEvaluation().isEnabled()).isFalse();
        assertThat(policyProperties.getEnforcement().isEnabled()).isFalse();
        assertThat(securityProperties.getDemoUsers()).hasSize(3);
        assertThat(securityProperties.getClaimMapping().getRolesClaim()).isEqualTo("roles");
    }
}
