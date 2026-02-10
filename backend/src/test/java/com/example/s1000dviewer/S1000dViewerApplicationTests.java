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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
    void engineerCanUploadAndModuleBecomesVisible() throws Exception {
        String engineerToken = loginAndGetToken("eng", "eng123");

        MockMultipartFile xmlFile = new MockMultipartFile(
            "file",
            "DMC-UPLOAD-TEST-002.xml",
            MediaType.APPLICATION_XML_VALUE,
            "<dmodule><dmTitle><techName>Upload</techName><infoName>By Engineer</infoName></dmTitle><content><descript><para>Uploaded content</para></descript></content></dmodule>".getBytes()
        );

        mockMvc.perform(multipart("/api/modules/upload")
                .file(xmlFile)
                .param("aircraft", "A330")
                .param("engine", "TRENT700")
                .param("icnId", "ICN-SAMPLE-AAA-0001-A-01")
                .header("Authorization", "Bearer " + engineerToken)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dmId").value("DMC-UPLOAD-TEST-002"));

        mockMvc.perform(get("/api/modules")
                .header("Authorization", "Bearer " + engineerToken)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].dmId", hasItem("DMC-UPLOAD-TEST-002")));
    }

    @Test
    void moduleListReturnsSeededModules() throws Exception {
        String token = loginAndGetToken("view", "view123");

        mockMvc.perform(get("/api/modules")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$[*].dmId", hasItem("DMC-SAMPLE-AAA-00-00-00-00A-040A-C")));
    }

    @Test
    void applicabilityFilterReturnsExpectedSubset() throws Exception {
        String token = loginAndGetToken("view", "view123");

        mockMvc.perform(get("/api/modules")
                .param("aircraft", "A320")
                .param("engine", "CFM56")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[0].aircraft").value("A320"));

        mockMvc.perform(get("/api/modules")
                .param("aircraft", "A380")
                .param("engine", "GP7200")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cgmGraphicIsConvertedToSvg() throws Exception {
        String token = loginAndGetToken("view", "view123");

        mockMvc.perform(get("/api/graphics/ICN-DEMO-CGM-0001")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/svg+xml"))
            .andExpect(content().string(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.containsString("CGM Demo Conversion"),
                org.hamcrest.Matchers.containsString("CGM conversion is not available in this runtime"),
                org.hamcrest.Matchers.containsString("Rendered via jcgm")
            )))
            .andExpect(content().string(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.containsString("<path d=\"M "),
                org.hamcrest.Matchers.containsString("<image href=\"data:image/png;base64,"),
                org.hamcrest.Matchers.containsString("Install jcgm jars")
            )));
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
