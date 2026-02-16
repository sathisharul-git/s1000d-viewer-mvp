package com.s1000Dorg.viewer.modules;

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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModuleApplicabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void moduleListFiltersOutNotApplicableButIncludesUnknownWithReasonAndSource() throws Exception {
        String token = loginAndGetToken("view", "view123");

        mockMvc.perform(get("/api/modules")
                .param("aircraft", "A320")
                .param("engine", "CFM56")
                .param("variant", "MOD-12")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filters.variant").value("MOD-12"))
            .andExpect(jsonPath("$.modules[*].dmId", hasItem("DMC-SAMPLE-AAA-00-00-00-00A-040A-C")))
            .andExpect(jsonPath("$.modules[?(@.dmId=='DMC-SAMPLE-AAA-00-00-00-00A-040A-C')].applicabilityResult").value(hasItem("APPLICABLE")))
            .andExpect(jsonPath("$.modules[?(@.dmId=='DMC-SAMPLE-AAA-00-00-00-00A-040A-C')].applicabilitySource").value(hasItem("published")))
            .andExpect(jsonPath("$.modules[?(@.dmId=='DMC-SAMPLE-AAA-00-00-00-00A-050A-C')]").isEmpty())
            .andExpect(jsonPath("$.modules[?(@.dmId=='DMC-SAMPLE-AAA-00-00-00-00A-060A-C')].applicabilityResult").value(hasItem("UNKNOWN")))
            .andExpect(jsonPath("$.modules[?(@.dmId=='DMC-SAMPLE-AAA-00-00-00-00A-060A-C')].applicabilitySource").value(hasItem("none")))
            .andExpect(content().string(containsString("unknown")));
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

