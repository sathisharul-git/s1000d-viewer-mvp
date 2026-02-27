package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.storage.VaultService;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InlineApplicabilityFilterServiceTest {

    private final DmApplicabilityEvaluator evaluator = new DmApplicabilityEvaluator(
        mock(FsDataRepository.class),
        mock(VaultService.class),
        mock(ApplicabilityService.class)
    );
    private final InlineApplicabilityFilterService filterService = new InlineApplicabilityFilterService(evaluator);

    @Test
    void removesElementsMarkedNotApplicableByApplicRefId() {
        String xml = """
            <dmodule>
              <content>
                <referencedApplicGroup>
                  <applic id="app-0001">
                    <evaluate andOr="and">
                      <assert applicPropertyIdent="type" applicPropertyValues="A320"/>
                    </evaluate>
                  </applic>
                </referencedApplicGroup>
                <proceduralStep id="keep-1"><para>Keep this step</para></proceduralStep>
                <proceduralStep id="drop-1" applicRefId="app-0001"><para>Drop this step</para></proceduralStep>
              </content>
            </dmodule>
            """;

        InlineApplicabilityFilterResult result = filterService.apply(
            xml,
            ApplicabilityContext.of(null, null, null, Map.of("type", "A350"))
        );

        assertThat(result.mode()).isEqualTo("FILTERED");
        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(result.xml()).contains("keep-1");
        assertThat(result.xml()).doesNotContain("drop-1");
    }

    @Test
    void resolvesApplicRefAliasToApplicDefinition() {
        String xml = """
            <dmodule>
              <content>
                <referencedApplicGroup>
                  <applic id="app-0002">
                    <evaluate andOr="and">
                      <assert applicPropertyIdent="model" applicPropertyValues="M1"/>
                    </evaluate>
                  </applic>
                </referencedApplicGroup>
                <applicRef id="ref-1" applicIdentValue="app-0002"/>
                <levelledPara id="drop-2" applicRefId="ref-1">
                  <title>Should be removed</title>
                </levelledPara>
              </content>
            </dmodule>
            """;

        InlineApplicabilityFilterResult result = filterService.apply(
            xml,
            ApplicabilityContext.of(null, null, null, Map.of("model", "M2"))
        );

        assertThat(result.mode()).isEqualTo("FILTERED");
        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(result.xml()).doesNotContain("drop-2");
    }
}
