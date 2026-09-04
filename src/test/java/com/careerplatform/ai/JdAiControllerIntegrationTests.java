package com.careerplatform.ai;

import com.careerplatform.ai.client.AiChatGateway;
import com.careerplatform.ai.dto.JdParseAiResult;
import com.careerplatform.ai.dto.JdRequirementAiCandidate;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.career.enums.RequirementType;
import com.careerplatform.career.service.CareerService;
import com.careerplatform.user.mapper.AppUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JdAiControllerIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CareerService careerService;
    @Autowired private AppUserMapper appUserMapper;

    @MockitoBean
    private AiChatGateway aiChatGateway;

    private AuthSession committedFixtureOwner;
    private Long committedFixtureCompanyId;
    private Long committedFixtureJobId;

    @AfterEach
    void cleanUpCommittedFixture() {
        if (committedFixtureJobId != null && committedFixtureOwner != null) {
            careerService.deleteJob(committedFixtureJobId, committedFixtureOwner.userId());
        }
        if (committedFixtureCompanyId != null && committedFixtureOwner != null) {
            careerService.deleteCompany(committedFixtureCompanyId, committedFixtureOwner.userId());
        }
        if (committedFixtureOwner != null) {
            appUserMapper.deleteById(committedFixtureOwner.userId());
        }
    }

    @Test
    @Transactional
    void parseShouldResolveSkillsFilterUnsupportedEvidenceDeduplicateAndNeverPersist() throws Exception {
        AuthSession owner = registerAndLogin("ai_parse_");
        String skillName = "Java-" + suffix();
        Long skillId = createSkill(owner, skillName);
        Long jobId = createJob(owner, createCompany(owner),
                "Requires " + skillName + " and three years of backend experience.");
        createRequirement(owner, jobId, "EXPERIENCE", null, "three years of backend experience");

        JdParseAiResult result = result(List.of(
                candidate(RequirementType.SKILL, "Use " + skillName, skillName, "Requires " + skillName),
                candidate(RequirementType.SKILL, "Use " + skillName, skillName, "Requires " + skillName),
                candidate(RequirementType.EXPERIENCE, "three years of backend experience", null,
                        "three years of backend experience"),
                candidate(RequirementType.OTHER, "Invented requirement", null, "not present in the JD")));
        stubResult(result);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceFingerprint").isString())
                .andExpect(jsonPath("$.requirements.length()").value(2))
                .andExpect(jsonPath("$.requirements[0].matchedSkillId").value(skillId))
                .andExpect(jsonPath("$.requirements[0].resolutionStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.requirements[0].duplicateStatus").value("NEW"))
                .andExpect(jsonPath("$.requirements[1].duplicateStatus").value("DUPLICATE_EXISTING"))
                .andExpect(jsonPath("$.requirements[1].selected").value(false))
                .andExpect(jsonPath("$.warnings.length()").value(2));

        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @Transactional
    void parseShouldEnforceOwnerIsolationAndRejectBlankJd() throws Exception {
        AuthSession owner = registerAndLogin("ai_owner_");
        AuthSession other = registerAndLogin("ai_other_");
        Long jobId = createJob(owner, createCompany(owner), "   ");

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(aiChatGateway);
    }

    @Test
    @Transactional
    void confirmShouldPersistOnlySelectedCandidates() throws Exception {
        AuthSession owner = registerAndLogin("ai_confirm_");
        String rawJd = "Degree required. English required.";
        Long jobId = createJob(owner, createCompany(owner), rawJd);
        stubResult(result(List.of(candidate(RequirementType.EDUCATION, "Degree required", null, "Degree required"))));
        String fingerprint = parseFingerprint(owner, jobId);

        String body = """
                {"sourceFingerprint":"%s","requirements":[
                  {"selected":true,"requirementType":"EDUCATION","requirementText":"Degree required"},
                  {"selected":false,"requirementType":"LANGUAGE","requirementText":"English required"}
                ]}
                """.formatted(fingerprint);
        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse/confirm", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.createdRequirements[0].requirementType").value("EDUCATION"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @Transactional
    void confirmShouldRejectStaleFingerprint() throws Exception {
        AuthSession owner = registerAndLogin("ai_stale_");
        Long companyId = createCompany(owner);
        Long jobId = createJob(owner, companyId, "Original JD");
        stubResult(result(List.of()));
        String fingerprint = parseFingerprint(owner, jobId);

        mockMvc.perform(put("/api/v1/jobs/{jobId}", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(companyId, "Changed JD")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse/confirm", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(fingerprint, "OTHER", null, "Original JD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));
    }

    @Test
    @Transactional
    void confirmShouldRejectDuplicatesWithoutOverwritingManualRequirements() throws Exception {
        AuthSession owner = registerAndLogin("ai_duplicate_");
        Long jobId = createJob(owner, createCompany(owner), "Reliable services");
        createRequirement(owner, jobId, "OTHER", null, "Reliable services");
        stubResult(result(List.of()));
        String fingerprint = parseFingerprint(owner, jobId);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse/confirm", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(fingerprint, "OTHER", null, "  reliable   SERVICES ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidSkillShouldRollbackEveryCandidateInConfirmation() throws Exception {
        AuthSession owner = registerAndLogin("ai_rollback_");
        committedFixtureOwner = owner;
        committedFixtureCompanyId = createCompany(owner);
        Long jobId = createJob(owner, committedFixtureCompanyId, "Java and teamwork");
        committedFixtureJobId = jobId;
        stubResult(result(List.of()));
        String fingerprint = parseFingerprint(owner, jobId);
        String body = """
                {"sourceFingerprint":"%s","requirements":[
                  {"selected":true,"requirementType":"OTHER","requirementText":"Teamwork"},
                  {"selected":true,"requirementType":"SKILL","skillId":999999999,"requirementText":"Java"}
                ]}
                """.formatted(fingerprint);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse/confirm", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Transactional
    void malformedAndProviderFailuresShouldUseSanitizedAiErrorsAndNeverWrite() throws Exception {
        AuthSession owner = registerAndLogin("ai_failure_");
        Long jobId = createJob(owner, createCompany(owner), "Java required");
        when(aiChatGateway.generateStructured(anyString(), anyString(), eq(JdParseAiResult.class)))
                .thenReturn(null)
                .thenThrow(new AiProviderException("raw provider authorization detail"));

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));
        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("AI Provider 当前不可用，请稍后重试"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aiEndpointShouldRequireBearerAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void stubResult(JdParseAiResult result) {
        when(aiChatGateway.generateStructured(anyString(), anyString(), eq(JdParseAiResult.class))).thenReturn(result);
    }

    private String parseFingerprint(AuthSession owner, Long jobId) throws Exception {
        String content = mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceFingerprint").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("sourceFingerprint").asText();
    }

    private Long createCompany(AuthSession owner) throws Exception {
        return responseId(mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AI Company " + suffix() + "\"}"))
                .andExpect(status().isCreated()));
    }

    private Long createJob(AuthSession owner, Long companyId, String rawJd) throws Exception {
        return responseId(mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(companyId, rawJd)))
                .andExpect(status().isCreated()));
    }

    private Long createSkill(AuthSession owner, String name) throws Exception {
        return responseId(mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()));
    }

    private void createRequirement(AuthSession owner, Long jobId, String type, Long skillId, String text)
            throws Exception {
        String skill = skillId == null ? "" : ",\"skillId\":" + skillId;
        mockMvc.perform(post("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementType\":\"" + type + "\"" + skill
                                + ",\"requirementText\":\"" + text + "\"}"))
                .andExpect(status().isCreated());
    }

    private String jobJson(Long companyId, String rawJd) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "companyId", companyId,
                "title", "AI Job " + suffix(),
                "jobType", "FULL_TIME",
                "rawJd", rawJd,
                "sourceType", "MANUAL"));
    }

    private String confirmBody(String fingerprint, String type, Long skillId, String text) throws Exception {
        java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("selected", true);
        item.put("requirementType", type);
        if (skillId != null) item.put("skillId", skillId);
        item.put("requirementText", text);
        return objectMapper.writeValueAsString(java.util.Map.of(
                "sourceFingerprint", fingerprint,
                "requirements", List.of(item)));
    }

    private AuthSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String password = "correct-password";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(login);
        return new AuthSession(response.get("token").asText(), response.get("userId").asLong());
    }

    private Long responseId(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        JsonNode response = objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private static JdParseAiResult result(List<JdRequirementAiCandidate> candidates) {
        JdParseAiResult result = new JdParseAiResult();
        result.setRequirements(candidates);
        result.setWarnings(List.of());
        return result;
    }

    private static JdRequirementAiCandidate candidate(
            RequirementType type, String description, String skillName, String evidence) {
        JdRequirementAiCandidate candidate = new JdRequirementAiCandidate();
        candidate.setType(type);
        candidate.setDescription(description);
        candidate.setSkillName(skillName);
        candidate.setEvidenceQuote(evidence);
        return candidate;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record AuthSession(String token, Long userId) {
        String authorization() { return "Bearer " + token; }
    }
}
