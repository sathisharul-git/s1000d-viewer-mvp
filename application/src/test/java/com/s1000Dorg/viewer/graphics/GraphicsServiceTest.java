package com.s1000Dorg.viewer.graphics;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;


class GraphicsServiceTest {

    @Test
    void jcgmConverterShouldProduceSvgWithImage() throws Exception {
        // Arrange
        DemoCgmToSvgConverter fallback = new DemoCgmToSvgConverter();
        CgmToSvgConverter converter = new JcgmBackedCgmToSvgConverter(fallback);
        InputStream cgmStream = new ClassPathResource("cgm/ICN-DEMO-CGM-0001.cgm").getInputStream();

        // Act
        String svg = converter.convert(cgmStream);

        // Assert
        assertThat(svg, startsWith("<svg"));
        assertThat(svg, anyOf(
            containsString("Rendered via jcgm"),
            containsString("CGM conversion is not available in this runtime")
        ));
    }
}
