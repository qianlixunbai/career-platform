package com.careerplatform.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CareerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional
    void careerGoalShouldSupportOwnedCrud() throws Exception {
        AuthSession owner = registerAndLogin("career_goal_");
        Long goalId = responseId(mockMvc.perform(post("/api/v1/career-goals")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalJson("Backend Engineer", "ACTIVE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetPosition").value("Backend Engineer")));

        mockMvc.perform(get("/api/v1/career-goals").header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(goalId));
        mockMvc.perform(get("/api/v1/career-goals/{id}", goalId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(put("/api/v1/career-goals/{id}", goalId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalJson("Platform Engineer", "PAUSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPosition").value("Platform Engineer"))
                .andExpect(jsonPath("$.status").value("PAUSED"));
        mockMvc.perform(delete("/api/v1/career-goals/{id}", goalId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Transactional
    void companyAndJobShouldSupportOwnedFlowAndIdempotentArchive() throws Exception {
        AuthSession owner = registerAndLogin("career_job_");
        Long companyId = createCompany(owner, "Example Corp");
        Long jobId = createJob(owner, companyId, "Backend Engineer");

        mockMvc.perform(get("/api/v1/jobs").header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(jobId));
        mockMvc.perform(get("/api/v1/jobs/{id}", jobId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(companyId))
                .andExpect(jsonPath("$.archived").value(false));
        mockMvc.perform(put("/api/v1/jobs/{id}", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(companyId, "Platform Engineer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Platform Engineer"));

        mockMvc.perform(patch("/api/v1/jobs/{id}/archive", jobId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(patch("/api/v1/jobs/{id}/archive", jobId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get("/api/v1/jobs").header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/jobs").param("archived", "true").header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(jobId));
        mockMvc.perform(patch("/api/v1/jobs/{id}/unarchive", jobId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        mockMvc.perform(get("/api/v1/jobs").header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(jobId))
                .andExpect(jsonPath("$[0].archived").value(false));
        mockMvc.perform(get("/api/v1/jobs").param("archived", "true")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(delete("/api/v1/companies/{id}", companyId).header("Authorization", owner.authorization()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    @Test
    @Transactional
    void companyShouldSupportOwnedCrudWhenNotReferencedByJob() throws Exception {
        AuthSession owner = registerAndLogin("career_company_");
        Long companyId = createCompany(owner, "Independent Company");
        mockMvc.perform(get("/api/v1/companies").header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(companyId));
        mockMvc.perform(get("/api/v1/companies/{id}", companyId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Independent Company"));
        mockMvc.perform(put("/api/v1/companies/{id}", companyId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson("Updated Company")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Company"));
        mockMvc.perform(delete("/api/v1/companies/{id}", companyId).header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Transactional
    void invalidEnumJsonShouldReturnUnifiedValidationError() throws Exception {
        AuthSession owner = registerAndLogin("career_invalid_json_");

        mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":1,\"title\":\"x\",\"jobType\":\"NOT_A_TYPE\",\"sourceType\":\"COMPANY_WEBSITE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求格式无效"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @Transactional
    void malformedJsonShouldReturnUnifiedValidationError() throws Exception {
        AuthSession owner = registerAndLogin("career_malformed_json_");

        mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求格式无效"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @Transactional
    void invalidPathVariableShouldReturnUnifiedValidationError() throws Exception {
        AuthSession owner = registerAndLogin("career_invalid_path_");

        mockMvc.perform(get("/api/v1/jobs/not-a-number")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求参数格式无效"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @Transactional
    void oversizedCareerGoalNotesShouldBeRejectedBeforeDatabaseWrite() throws Exception {
        AuthSession owner = registerAndLogin("career_goal_text_");
        String body = goalJson("Backend Engineer", "ACTIVE")
                .replace("\"Prepare Java\"", objectMapper.writeValueAsString("x".repeat(16_001)));

        mockMvc.perform(post("/api/v1/career-goals")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/career-goals")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Transactional
    void oversizedCompanyNotesShouldBeRejectedBeforeDatabaseWrite() throws Exception {
        AuthSession owner = registerAndLogin("career_company_text_");
        String body = companyJson("Text Company")
                .replace("\"Watch campus hiring\"", objectMapper.writeValueAsString("x".repeat(16_001)));

        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Transactional
    void oversizedJobRawJdShouldBeRejectedBeforeDatabaseWrite() throws Exception {
        AuthSession owner = registerAndLogin("career_job_text_");
        Long companyId = createCompany(owner, "Text Job Company");
        String body = jobJson(companyId, "Text Job")
                .replace("\"Build reliable services\"", objectMapper.writeValueAsString("x".repeat(16_001)));

        mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/jobs")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Transactional
    void jobDeadlineBeforePublishDateShouldBeRejected() throws Exception {
        AuthSession owner = registerAndLogin("career_job_dates_");
        Long companyId = createCompany(owner, "Date Company");
        String body = jobJson(companyId, "Date Job")
                .replace("\"deadline\":\"2026-10-01\"", "\"deadline\":\"2026-08-31\"");

        mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Transactional
    void otherUserCannotReadUpdateOrDeletePrivateCareerResourcesOrUseForeignCompany() throws Exception {
        AuthSession owner = registerAndLogin("career_owner_");
        AuthSession other = registerAndLogin("career_other_");
        Long goalId = responseId(mockMvc.perform(post("/api/v1/career-goals")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalJson("Owner Goal", "ACTIVE")))
                .andExpect(status().isCreated()));
        Long companyId = createCompany(owner, "Owner Company");
        Long otherCompanyId = createCompany(other, "Other Company");
        Long jobId = createJob(owner, companyId, "Owner Job");

        assertOtherUserCannotCrud(other, "/api/v1/career-goals", goalId, goalJson("Stolen Goal", "PAUSED"));
        assertOtherUserCannotCrud(other, "/api/v1/companies", companyId, companyJson("Stolen Company"));
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(companyId, "Stolen Job")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/jobs/{id}", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(otherCompanyId, "Moved Job")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/jobs/{id}", jobId).header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/jobs/{id}", jobId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(companyId, "Stolen Job")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/career-goals/{id}", goalId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPosition").value("Owner Goal"));
        mockMvc.perform(get("/api/v1/jobs/{id}", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Owner Job"))
                .andExpect(jsonPath("$.companyId").value(companyId));
    }

    @Test
    @Transactional
    void requirementsAndNotesShouldBeScopedToOwnedJob() throws Exception {
        AuthSession owner = registerAndLogin("career_child_owner_");
        AuthSession other = registerAndLogin("career_child_other_");
        Long skillId = responseId(mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CareerSkill-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()));
        Long jobId = createJob(owner, createCompany(owner, "Child Company"), "Child Job");
        Long requirementId = responseId(mockMvc.perform(post("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementType\":\"SKILL\",\"skillId\":" + skillId + ",\"requirementText\":\"Java\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skillId").value(skillId)));
        Long noteId = responseId(mockMvc.perform(post("/api/v1/jobs/{jobId}/notes", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Campus hiring starts in September\"}"))
                .andExpect(status().isCreated()));

        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requirementId));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements/{id}", jobId, requirementId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementText").value("Java"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/notes", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(noteId));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/notes/{id}", jobId, noteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Campus hiring starts in September"));

        mockMvc.perform(put("/api/v1/jobs/{jobId}/requirements/{id}", jobId, requirementId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementType\":\"SKILL\",\"skillId\":" + skillId + ",\"requirementText\":\"Java 21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementText").value("Java 21"));
        mockMvc.perform(put("/api/v1/jobs/{jobId}/notes/{id}", jobId, noteId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Follow up with recruiter\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Follow up with recruiter"));

        mockMvc.perform(post("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementType\":\"OTHER\",\"requirementText\":\"Stolen\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements/{id}", jobId, requirementId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/jobs/{jobId}/requirements/{id}", jobId, requirementId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementType\":\"OTHER\",\"requirementText\":\"Stolen\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/jobs/{jobId}/notes", jobId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Stolen\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/notes/{id}", jobId, noteId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/jobs/{jobId}/notes/{id}", jobId, noteId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Stolen\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/jobs/{jobId}/requirements/{id}", jobId, requirementId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/jobs/{jobId}/notes/{id}", jobId, noteId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements/{id}", jobId, requirementId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementText").value("Java 21"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/notes/{id}", jobId, noteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Follow up with recruiter"));

        mockMvc.perform(delete("/api/v1/jobs/{jobId}/requirements/{id}", jobId, requirementId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/jobs/{jobId}/notes/{id}", jobId, noteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Transactional
    void skillRequirementShouldRequireExistingSkillAndOtherTypesCannotCarrySkill() throws Exception {
        AuthSession owner = registerAndLogin("career_validation_");
        Long jobId = createJob(owner, createCompany(owner, "Validation Company"), "Validation Job");
        mockMvc.perform(post("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementType\":\"SKILL\",\"requirementText\":\"Java\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementType\":\"SKILL\",\"skillId\":999999999,\"requirementText\":\"Missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementType\":\"OTHER\",\"skillId\":999999999,\"requirementText\":\"Other\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Long createCompany(AuthSession user, String name) throws Exception {
        return responseId(mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name)));
    }

    private Long createJob(AuthSession user, Long companyId, String title) throws Exception {
        return responseId(mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(companyId, title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(title)));
    }

    private void assertOtherUserCannotCrud(AuthSession other, String basePath, Long id, String updateJson)
            throws Exception {
        mockMvc.perform(get(basePath + "/{id}", id).header("Authorization", other.authorization()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put(basePath + "/{id}", id).header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete(basePath + "/{id}", id).header("Authorization", other.authorization()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private String companyJson(String name) {
        return "{\"name\":\"%s\",\"industry\":\"Internet\",\"city\":\"Shanghai\",\"website\":\"https://example.com\",\"size\":\"100-499\",\"notes\":\"Watch campus hiring\"}".formatted(name);
    }

    private String jobJson(Long companyId, String title) {
        return "{\"companyId\":%d,\"title\":\"%s\",\"city\":\"Shanghai\",\"jobType\":\"FULL_TIME\",\"publishDate\":\"2026-09-01\",\"deadline\":\"2026-10-01\",\"rawJd\":\"Build reliable services\",\"sourceType\":\"COMPANY_WEBSITE\",\"sourceName\":\"Careers\",\"sourceUrl\":\"https://example.com/jobs\"}".formatted(companyId, title);
    }

    private String goalJson(String targetPosition, String status) {
        return "{\"targetPosition\":\"%s\",\"targetCity\":\"Shanghai\",\"targetIndustry\":\"Internet\",\"targetCompanyPreference\":\"Product company\",\"salaryExpectation\":\"20k\",\"notes\":\"Prepare Java\",\"status\":\"%s\"}".formatted(targetPosition, status);
    }

    private AuthSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String password = "correct-password";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new AuthSession(objectMapper.readTree(login).get("token").asText());
    }

    private Long responseId(org.springframework.test.web.servlet.ResultActions resultActions) throws Exception {
        JsonNode response = objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private record AuthSession(String token) {
        String authorization() { return "Bearer " + token; }
    }
}
