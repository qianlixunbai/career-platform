package com.careerplatform.ai;

import com.careerplatform.ai.client.AiChatGateway;
import com.careerplatform.ai.dto.JdParseAiResult;
import com.careerplatform.ai.dto.JdRequirementAiCandidate;
import com.careerplatform.career.enums.RequirementType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JdAiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiChatGateway aiChatGateway;

    @Test
    @Transactional
    void parseReturnsTypedPreviewAndDoesNotPersistRequirements() throws Exception {
        AuthSession owner = registerAndLogin("ai_preview_");
        Long companyId = createCompany(owner, "AI Preview Company");
        String rawJd = "Build Java services. Bachelor degree preferred.";
        Long jobId = createJob(owner, companyId, "Preview Job", rawJd);
        String javaSkillName = unique("Java");
        Long javaSkillId = createSkill(owner, javaSkillName);

        when(aiChatGateway.generateStructured(anyString(), anyString(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(
                        candidate(RequirementType.SKILL, "Java", javaSkillName, "Build Java services"),
                        candidate(RequirementType.EDUCATION, "Bachelor degree", null, "Bachelor degree")));

        String response = parse(owner, jobId);
        JsonNode body = objectMapper.readTree(response);

        assertThat(body.path("sourceFingerprint").asText()).hasSize(64);
        assertThat(body.path("requirements").size()).isEqualTo(2);
        assertThat(body.path("requirements").get(0).path("requirementType").asText()).isEqualTo("SKILL");
        assertThat(body.path("requirements").get(0).path("matchedSkillId").asLong()).isEqualTo(javaSkillId);
        assertThat(body.path("requirements").get(0).path("resolutionStatus").asText()).isEqualTo("RESOLVED");
        assertThat(body.path("requirements").get(0).path("selected").asBoolean()).isTrue();
        assertThat(body.path("requirements").get(1).path("resolutionStatus").asText()).isEqualTo("NOT_APPLICABLE");
        assertThat(body.path("requirements").get(1).path("selected").asBoolean()).isTrue();

        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(aiChatGateway).generateStructured(
                anyString(), org.mockito.ArgumentMatchers.contains(rawJd), eq(JdParseAiResult.class));
    }

    @Test
    @Transactional
    void parseEnforcesJobOwnershipBeforeCallingAi() throws Exception {
        AuthSession owner = registerAndLogin("ai_owner_");
        AuthSession other = registerAndLogin("ai_other_");
        Long companyId = createCompany(owner, "Private AI Company");
        Long jobId = createJob(owner, companyId, "Private AI Job", "Java");

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        verify(aiChatGateway, never()).generateStructured(anyString(), anyString(), eq(JdParseAiResult.class));
    }

    @Test
    @Transactional
    void blankJdIsRejectedBeforeCallingAi() throws Exception {
        AuthSession owner = registerAndLogin("ai_blank_");
        Long companyId = createCompany(owner, "Blank JD Company");
        Long jobId = createJob(owner, companyId, "Blank JD Job", "  \n\t");

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("岗位原始 JD 不能为空"));

        verify(aiChatGateway, never()).generateStructured(anyString(), anyString(), eq(JdParseAiResult.class));
    }

    @Test
    @Transactional
    void unsupportedEvidenceIsDroppedWithWarningAndDoesNotWriteRequirements() throws Exception {
        AuthSession owner = registerAndLogin("ai_evidence_");
        Long companyId = createCompany(owner, "Evidence Company");
        Long jobId = createJob(owner, companyId, "Evidence Job", "The role uses Java.");
        when(aiChatGateway.generateStructured(anyString(), anyString(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(candidate(RequirementType.SKILL, "Python", "Python", "Python is absent")));

        JsonNode body = objectMapper.readTree(parse(owner, jobId));

        assertThat(body.path("requirements").size()).isZero();
        assertThat(body.path("warnings").toString())
                .contains("已丢弃第1条候选：证据无法在当前 JD 中找到");
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Transactional
    void skillsAreResolvedOrLeftUnresolvedWithoutAutoCreatingDictionaryRows() throws Exception {
        AuthSession owner = registerAndLogin("ai_skill_resolution_");
        Long companyId = createCompany(owner, "Skill Resolution Company");
        String unresolvedName = unique("UnresolvedSkill");
        Long jobId = createJob(owner, companyId, "Skill Resolution Job",
                "Java and " + unresolvedName + " are required.");
        String javaSkillName = unique("Java");
        Long javaSkillId = createSkill(owner, javaSkillName);
        when(aiChatGateway.generateStructured(anyString(), anyString(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(
                        candidate(RequirementType.SKILL, "Java", javaSkillName, "Java"),
                        candidate(RequirementType.SKILL, unresolvedName, unresolvedName, unresolvedName)));

        JsonNode body = objectMapper.readTree(parse(owner, jobId));
        JsonNode resolved = body.path("requirements").get(0);
        JsonNode unresolved = body.path("requirements").get(1);

        assertThat(resolved.path("resolutionStatus").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.path("matchedSkillId").asLong()).isEqualTo(javaSkillId);
        assertThat(resolved.path("selected").asBoolean()).isTrue();
        assertThat(unresolved.path("resolutionStatus").asText()).isEqualTo("UNRESOLVED");
        assertThat(unresolved.path("matchedSkillId").isNull()).isTrue();
        assertThat(unresolved.path("selected").asBoolean()).isFalse();

        JsonNode allSkills = objectMapper.readTree(mockMvc.perform(get("/api/v1/skills")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        boolean unresolvedWasCreated = false;
        for (JsonNode skill : allSkills) {
            if (unresolvedName.equals(skill.path("name").asText())) {
                unresolvedWasCreated = true;
                break;
            }
        }
        assertThat(unresolvedWasCreated).isFalse();
    }

    @Test
    @Transactional
    void aiDuplicatesAndExistingRequirementsAreSeparatedInPreview() throws Exception {
        AuthSession owner = registerAndLogin("ai_duplicate_");
        Long companyId = createCompany(owner, "Duplicate Company");
        String skillName = unique("Java");
        String rawJd = "Java backend and bachelor degree are required.";
        Long jobId = createJob(owner, companyId, "Duplicate Job", rawJd);
        Long skillId = createSkill(owner, skillName);
        createRequirement(owner, jobId, "SKILL", skillId, "Java backend");
        when(aiChatGateway.generateStructured(anyString(), anyString(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(
                        candidate(RequirementType.SKILL, "Java backend", skillName, "Java backend"),
                        candidate(RequirementType.SKILL, "Java backend", skillName, "Java backend"),
                        candidate(RequirementType.EDUCATION, "bachelor degree", null, "bachelor degree")));

        JsonNode body = objectMapper.readTree(parse(owner, jobId));

        assertThat(body.path("requirements").size()).isEqualTo(2);
        assertThat(body.path("requirements").get(0).path("duplicateStatus").asText())
                .isEqualTo("DUPLICATE_EXISTING");
        assertThat(body.path("requirements").get(0).path("selected").asBoolean()).isFalse();
        assertThat(body.path("requirements").get(1).path("duplicateStatus").asText()).isEqualTo("NEW");
        assertThat(body.path("requirements").get(1).path("selected").asBoolean()).isTrue();
        assertThat(body.path("warnings").toString()).contains("已丢弃第2条候选：AI 返回了重复要求");
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @Transactional
    void confirmPersistsSelectedPreviewRowsAndIgnoresUnselectedRows() throws Exception {
        AuthSession owner = registerAndLogin("ai_confirm_");
        Long companyId = createCompany(owner, "Confirm Company");
        String rawJd = "Java backend. Bachelor degree.";
        Long jobId = createJob(owner, companyId, "Confirm Job", rawJd);
        String skillName = unique("Java");
        Long skillId = createSkill(owner, skillName);
        when(aiChatGateway.generateStructured(anyString(), anyString(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(
                        candidate(RequirementType.SKILL, "Java backend", skillName, "Java backend"),
                        candidate(RequirementType.EDUCATION, "Bachelor degree", null, "Bachelor degree")));

        JsonNode preview = objectMapper.readTree(parse(owner, jobId));
        String confirmation = confirmationJson(preview, true, false);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse/confirm", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmation))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.createdRequirements[0].skillId").value(skillId))
                .andExpect(jsonPath("$.createdRequirements[0].requirementText").value("Java backend"));

        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].requirementText").value("Java backend"));
    }

    @Test
    @Transactional
    void changedJdMakesConfirmationStaleAndLeavesRowsUntouched() throws Exception {
        AuthSession owner = registerAndLogin("ai_stale_");
        Long companyId = createCompany(owner, "Stale Company");
        Long jobId = createJob(owner, companyId, "Stale Job", "Old requirement.");
        when(aiChatGateway.generateStructured(anyString(), anyString(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(candidate(RequirementType.OTHER, "Old requirement", null, "Old requirement")));

        JsonNode preview = objectMapper.readTree(parse(owner, jobId));
        mockMvc.perform(put("/api/v1/jobs/{id}", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(companyId, "Stale Job", "Changed requirement.")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse/confirm", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationJson(preview, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Transactional
    void selectedSkillWithoutSkillIdIsRejectedBeforeAnyWrite() throws Exception {
        AuthSession owner = registerAndLogin("ai_invalid_skill_");
        Long companyId = createCompany(owner, "Invalid Skill Company");
        String rawJd = "Java requirement.";
        Long jobId = createJob(owner, companyId, "Invalid Skill Job", rawJd);
        ObjectNode request = objectMapper.createObjectNode();
        request.put("sourceFingerprint", fingerprint(rawJd));
        ArrayNode requirements = request.putArray("requirements");
        ObjectNode item = requirements.addObject();
        item.put("selected", true);
        item.put("requirementType", "SKILL");
        item.put("requirementText", "Java");

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse/confirm", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("技能要求必须关联技能"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void oneInvalidSkillRollsBackEarlierConfirmedRows() throws Exception {
        AuthSession owner = registerAndLogin("ai_rollback_");
        Long companyId = createCompany(owner, "Rollback Company");
        String rawJd = "Java requirements.";
        Long jobId = createJob(owner, companyId, "Rollback Job", rawJd);
        Long skillId = createSkill(owner, unique("Java"));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("sourceFingerprint", fingerprint(rawJd));
        ArrayNode requirements = request.putArray("requirements");
        addConfirmItem(requirements, true, "SKILL", skillId, "Java");
        addConfirmItem(requirements, true, "SKILL", Long.MAX_VALUE, "Missing skill");

        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse/confirm", jobId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void parseRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", 999_999L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(aiChatGateway, never()).generateStructured(anyString(), anyString(), eq(JdParseAiResult.class));
    }

    private String parse(AuthSession user, Long jobId) throws Exception {
        return mockMvc.perform(post("/api/v1/jobs/{jobId}/ai/jd-parse", jobId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long createCompany(AuthSession user, String name) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        body.put("industry", "Internet");
        body.put("city", "Shanghai");
        body.put("website", "https://example.com");
        body.put("size", "100-499");
        body.put("notes", "AI test company");
        return responseId(mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated()));
    }

    private Long createJob(AuthSession user, Long companyId, String title, String rawJd) throws Exception {
        return responseId(mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(companyId, title, rawJd)))
                .andExpect(status().isCreated()));
    }

    private Long createSkill(AuthSession user, String name) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        return responseId(mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated()));
    }

    private Long createRequirement(
            AuthSession user, Long jobId, String type, Long skillId, String text) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("requirementType", type);
        if (skillId != null) {
            body.put("skillId", skillId);
        }
        body.put("requirementText", text);
        return responseId(mockMvc.perform(post("/api/v1/jobs/{jobId}/requirements", jobId)
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated()));
    }

    private String jobJson(Long companyId, String title, String rawJd) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("companyId", companyId);
        body.put("title", title);
        body.put("city", "Shanghai");
        body.put("jobType", "FULL_TIME");
        body.put("publishDate", "2026-09-01");
        body.put("deadline", "2026-10-01");
        body.put("rawJd", rawJd);
        body.put("sourceType", "COMPANY_WEBSITE");
        body.put("sourceName", "Careers");
        body.put("sourceUrl", "https://example.com/jobs");
        return body.toString();
    }

    private String confirmationJson(JsonNode preview, boolean... selectedValues) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("sourceFingerprint", preview.path("sourceFingerprint").asText());
        ArrayNode requirements = body.putArray("requirements");
        for (int index = 0; index < preview.path("requirements").size(); index++) {
            JsonNode candidate = preview.path("requirements").get(index);
            boolean selected = index < selectedValues.length && selectedValues[index];
            ObjectNode item = requirements.addObject();
            item.put("selected", selected);
            item.put("requirementType", candidate.path("requirementType").asText());
            item.put("requirementText", candidate.path("description").asText());
            if ("SKILL".equals(candidate.path("requirementType").asText())
                    && !candidate.path("matchedSkillId").isNull()) {
                item.put("skillId", candidate.path("matchedSkillId").asLong());
            }
        }
        return body.toString();
    }

    private void addConfirmItem(
            ArrayNode requirements, boolean selected, String type, Long skillId, String text) {
        ObjectNode item = requirements.addObject();
        item.put("selected", selected);
        item.put("requirementType", type);
        item.put("skillId", skillId);
        item.put("requirementText", text);
    }

    private JdParseAiResult aiResult(JdRequirementAiCandidate... candidates) {
        JdParseAiResult result = new JdParseAiResult();
        result.setRequirements(List.of(candidates));
        result.setWarnings(List.of());
        return result;
    }

    private JdRequirementAiCandidate candidate(
            RequirementType type, String description, String skillName, String evidence) {
        JdRequirementAiCandidate candidate = new JdRequirementAiCandidate();
        candidate.setType(type);
        candidate.setDescription(description);
        candidate.setSkillName(skillName);
        candidate.setEvidenceQuote(evidence);
        return candidate;
    }

    private AuthSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String password = "correct-password";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new AuthSession(objectMapper.readTree(login).path("token").asText());
    }

    private Long responseId(ResultActions resultActions) throws Exception {
        return objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString())
                .path("id").asLong();
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String fingerprint(String value) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private record AuthSession(String token) {
        String authorization() {
            return "Bearer " + token;
        }
    }
}
