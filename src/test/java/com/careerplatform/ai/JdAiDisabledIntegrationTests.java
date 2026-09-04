package com.careerplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JdAiDisabledIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @Transactional
    void disabledAiShouldReturn503WithoutBlockingTraditionalJobCreation() throws Exception {
        String username = "ai_disabled_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String password = "correct-password";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + objectMapper.readTree(login).get("token").asText();
        Long companyId = objectMapper.readTree(mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Disabled AI Company\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        String job = objectMapper.writeValueAsString(java.util.Map.of(
                "companyId", companyId, "title", "Manual Job", "jobType", "FULL_TIME",
                "rawJd", "Java required", "sourceType", "MANUAL"));
        Long jobId = objectMapper.readTree(mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(job))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId).header("Authorization", auth))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"));
        mockMvc.perform(post("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementType\":\"OTHER\",\"requirementText\":\"Manual entry still works\"}"))
                .andExpect(status().isCreated());
    }
}
