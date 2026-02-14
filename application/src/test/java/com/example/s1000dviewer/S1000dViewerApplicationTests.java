package com.example.s1000dviewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class S1000dViewerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginWorksAndViewerIsReadOnlyForUpload() throws Exception {
        String viewerToken = loginAndGetToken("view", "view123");

        MockMultipartFile xmlFile = new MockMultipartFile(
            "file",
            "DMC-UPLOAD-TEST-001.xml",
            MediaType.APPLICATION_XML_VALUE,
            "<dmodule><dmTitle><techName>Upload</techName><infoName>Test</infoName></dmTitle><content><descript><para>Example</para></descript></content></dmodule>".getBytes()
        );

        mockMvc.perform(multipart("/api/modules/upload")
                .file(xmlFile)
                .param("aircraft", "A350")
                .param("engine", "XWB")
                .header("Authorization", "Bearer " + viewerToken)
        )
            .andExpect(status().isForbidden());
    }

    @Test
    void publishedPreviewIsPreferredWhenAvailable() throws Exception {
        String token = loginAndGetToken("view", "view123");

        mockMvc.perform(get("/api/modules/DMC-SAMPLE-AAA-00-00-00-00A-040A-C/render")
                .param("aircraft", "A320")
                .param("engine", "CFM56")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("published"))
            .andExpect(jsonPath("$.meta.applicabilityResult").value("APPLICABLE"))
            .andExpect(jsonPath("$.assets.icns", hasItem("ICN-SAMPLE-AAA-0001-A-01")));
    }

    @Test
    void quickPreviewIsUsedWhenPublishedIsMissing() throws Exception {
        String token = loginAndGetToken("view", "view123");

        mockMvc.perform(get("/api/modules/DMC-SAMPLE-AAA-00-00-00-00A-050A-C/render")
                .param("aircraft", "A350")
                .param("engine", "XWB")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("quick"))
            .andExpect(content().string(containsString("Inspect lever")));
    }

    @Test
    void phase1ApplicabilityFilteringWorksAndUnknownIsIncluded() throws Exception {
        String token = loginAndGetToken("view", "view123");

        mockMvc.perform(get("/api/modules")
                .param("aircraft", "A320")
                .param("engine", "CFM56")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules[*].dmId", hasItem("DMC-SAMPLE-AAA-00-00-00-00A-040A-C")));

        mockMvc.perform(get("/api/modules")
                .param("aircraft", "A380")
                .param("engine", "GP7200")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules[*].dmId", hasItem("DMC-SAMPLE-AAA-00-00-00-00A-060A-C")));
    }

    @Test
    void unknownApplicabilityIsTaggedInRenderResponse() throws Exception {
        String token = loginAndGetToken("view", "view123");

        mockMvc.perform(get("/api/modules/DMC-SAMPLE-AAA-00-00-00-00A-060A-C/render")
                .param("aircraft", "A220")
                .param("engine", "PW1500G")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.applicabilityResult").value("UNKNOWN"));
    }

    @Test
    void malformedXmlUploadIsRejected() throws Exception {
        String token = loginAndGetToken("eng", "eng123");

        MockMultipartFile malformedXml = new MockMultipartFile(
            "file",
            "DMC-UPLOAD-MALFORMED.xml",
            MediaType.APPLICATION_XML_VALUE,
            "<dmodule><content><para>broken".getBytes()
        );

        mockMvc.perform(multipart("/api/modules/upload")
                .file(malformedXml)
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isBadRequest());
    }

    @Test
    void cgmGraphicIsConvertedToSvg() throws Exception {
        String token = loginAndGetToken("view", "view123");

        mockMvc.perform(get("/api/graphics/ICN-DEMO-CGM-0001")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/svg+xml"));
    }

    @Test
    void adminEndpointRequiresAdminRole() throws Exception {
        String viewerToken = loginAndGetToken("view", "view123");
        String adminToken = loginAndGetToken("admin", "admin123");

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + viewerToken)
        )
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].username", hasItem("admin")));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String payload = objectMapper.writeValueAsString(new LoginPayload(username, password));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private record LoginPayload(String username, String password) {
    }
}
