package com.s1000Dorg.viewer.pmc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicationModulesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicationModulesArePmcOrderedAndFilteredByApplicability() throws Exception {
        String token = loginAndGetToken("view", "view123");
        String pmcId = "PMC-SAMPLE-C3002-EPWG1-00_000-01_EN-US";

        mockMvc.perform(get("/api/publications/{pmcId}/modules", pmcId)
                .param("aircraft", "A320")
                .param("engine", "CFM56")
                .param("variant", "MOD-12")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pmcId").value(pmcId))
            .andExpect(jsonPath("$.modules[*].dmId", hasItem("DMC-SAMPLE-AAA-00-00-00-00A-040A-C")))
            .andExpect(jsonPath("$.modules[*].dmId", hasItem("DMC-SAMPLE-AAA-00-00-00-00A-060A-C")))
            .andExpect(jsonPath("$.modules[*].dmId", not(hasItem("DMC-SAMPLE-AAA-00-00-00-00A-050A-C"))))
            .andExpect(jsonPath("$.modules[0].dmId").value("DMC-SAMPLE-AAA-00-00-00-00A-040A-C"))
            .andExpect(jsonPath("$.modules[0].dmApplicabilityStatus").value("APPLICABLE"));
    }

    @Test
    void renderAcceptsPmcScopeAndRejectsDmOutsidePublication() throws Exception {
        String token = loginAndGetToken("view", "view123");
        String pmcId = "PMC-SAMPLE-C3002-EPWG1-00_000-01_EN-US";

        mockMvc.perform(get("/api/modules/DMC-SAMPLE-AAA-00-00-00-00A-040A-C/render")
                .param("pmcId", pmcId)
                .param("aircraft", "A320")
                .param("engine", "CFM56")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicability.dmStatus").value("APPLICABLE"))
            .andExpect(jsonPath("$.applicability.reason").isNotEmpty());

        mockMvc.perform(get("/api/modules/DMC-UPLOAD-TEST-002/render")
                .param("pmcId", pmcId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String payload = objectMapper.writeValueAsString(new LoginPayload(username, password));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private record LoginPayload(String username, String password) {
    }
}
