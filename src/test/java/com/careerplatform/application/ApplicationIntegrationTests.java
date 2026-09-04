package com.careerplatform.application;

import com.careerplatform.application.dto.CreateApplicationRequest;
import com.careerplatform.application.service.ApplicationService;
import com.careerplatform.common.exception.DuplicateResourceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationService applicationService;

    @Test
    void createRequiresOwnedFinalizedResumeVersion() throws Exception {
        AuthSession owner = registerAndLogin();
        AuthSession other = registerAndLogin();
        Long companyId = createCompany(owner, "创建约束公司");
        Long jobId = createJob(owner, companyId, "Java 工程师");
        ResumeFixture draft = createResumeVersion(owner, false);
        ResumeFixture finalized = createResumeVersion(owner, true);
        Long otherCompanyId = createCompany(other, "其他用户公司");
        Long otherJobId = createJob(other, otherCompanyId, "其他岗位");
        ResumeFixture otherFinalized = createResumeVersion(other, true);

        mockMvc.perform(post("/api/v1/applications").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(jobId, finalized.versionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentStage").value("APPLIED"))
                .andExpect(jsonPath("$.jobTitleSnapshot").value("Java 工程师"));

        mockMvc.perform(post("/api/v1/applications").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(createJob(owner, companyId, "草稿校验岗位"), draft.versionId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));

        mockMvc.perform(post("/api/v1/applications").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(otherJobId, finalized.versionId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/applications").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(createJob(owner, companyId, "简历归属校验岗位"), otherFinalized.versionId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void uniqueOngoingAndHistoricalReapplicationShouldBothHold() throws Exception {
        AuthSession user = registerAndLogin();
        Long companyId = createCompany(user, "重复投递公司");
        Long jobId = createJob(user, companyId, "后端开发");
        ResumeFixture resume = createResumeVersion(user, true);
        Long firstId = createApplication(user, jobId, resume.versionId());

        mockMvc.perform(post("/api/v1/applications").header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(applicationJson(jobId, resume.versionId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        transition(user, firstId, "ENDED", "NO_RESPONSE", "长期未收到回复");
        Long secondId = createApplication(user, jobId, resume.versionId());
        assertThat(secondId).isNotEqualTo(firstId);
        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application WHERE user_id = ? AND job_id = ?", Integer.class,
                user.userId(), jobId);
        assertThat(historyCount).isEqualTo(2);
    }

    @Test
    void concurrentCreateAllowsExactlyOneOngoingApplication() throws Exception {
        AuthSession user = registerAndLogin();
        Long companyId = createCompany(user, "并发公司");
        Long jobId = createJob(user, companyId, "并发岗位");
        ResumeFixture resume = createResumeVersion(user, true);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    CreateApplicationRequest request = new CreateApplicationRequest();
                    request.setJobId(jobId);
                    request.setResumeVersionId(resume.versionId());
                    try {
                        return applicationService.createApplication(user.userId(), request);
                    } catch (DuplicateResourceException exception) {
                        return exception;
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Object> results = List.of(futures.get(0).get(), futures.get(1).get());
            assertThat(results.stream().filter(value -> value instanceof com.careerplatform.application.entity.Application)).hasSize(1);
            assertThat(results.stream().filter(value -> value instanceof DuplicateResourceException)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
        Integer ongoingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application WHERE user_id = ? AND job_id = ? AND current_stage <> 'ENDED'",
                Integer.class, user.userId(), jobId);
        assertThat(ongoingCount).isEqualTo(1);
    }

    @Test
    void transitionsAreForwardOnlyAndHistoryRemainsAtomic() throws Exception {
        AuthSession user = registerAndLogin();
        Long companyId = createCompany(user, "状态机公司");
        Long jobId = createJob(user, companyId, "状态机岗位");
        Long applicationId = createApplication(user, jobId, createResumeVersion(user, true).versionId());

        transition(user, applicationId, "INTERVIEW", null, "跳过测评");
        int historyBeforeFailure = historyCount(applicationId);
        mockMvc.perform(post("/api/v1/applications/{id}/transitions", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStage\":\"ASSESSMENT\",\"note\":\"非法回退\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));
        assertThat(historyCount(applicationId)).isEqualTo(historyBeforeFailure);

        transition(user, applicationId, "ENDED", "COMPANY_REJECTED", "流程结束");
        mockMvc.perform(post("/api/v1/applications/{id}/transitions", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStage\":\"OFFER\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/applications/{id}", applicationId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("ENDED"));
        assertThat(historyCount(applicationId)).isEqualTo(3);
    }

    @Test
    void assessmentsAndInterviewsNeverMutateApplicationStage() throws Exception {
        AuthSession user = registerAndLogin();
        Long companyId = createCompany(user, "局部记录公司");
        Long jobId = createJob(user, companyId, "局部记录岗位");
        Long applicationId = createApplication(user, jobId, createResumeVersion(user, true).versionId());

        String assessment = mockMvc.perform(post("/api/v1/applications/{id}/assessments", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CODING_TEST\",\"title\":\"在线编程\",\"result\":\"FAILED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long assessmentId = objectMapper.readTree(assessment).get("id").asLong();
        mockMvc.perform(put("/api/v1/applications/{id}/assessments/{childId}", applicationId, assessmentId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CODING_TEST\",\"title\":\"在线编程\",\"result\":\"PASSED\"}"))
                .andExpect(status().isOk());

        String interview = mockMvc.perform(post("/api/v1/applications/{id}/interviews", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundNo\":1,\"type\":\"TECHNICAL\",\"title\":\"技术一面\",\"result\":\"REJECTED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long interviewId = objectMapper.readTree(interview).get("id").asLong();
        mockMvc.perform(put("/api/v1/applications/{id}/interviews/{childId}", applicationId, interviewId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundNo\":1,\"type\":\"TECHNICAL\",\"title\":\"技术一面\",\"result\":\"PASSED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/{id}", applicationId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("APPLIED"));
        assertThat(historyCount(applicationId)).isEqualTo(1);
    }

    @Test
    void offerTerminalSemanticsAndFinalReviewUpsertAreAtomic() throws Exception {
        AuthSession user = registerAndLogin();
        Long companyId = createCompany(user, "Offer 公司");
        Long jobId = createJob(user, companyId, "Offer 岗位");
        ResumeFixture resume = createResumeVersion(user, true);
        Long applicationId = createApplication(user, jobId, resume.versionId());

        mockMvc.perform(put("/api/v1/applications/{id}/final-review", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("过早复盘")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));

        createOffer(user, applicationId);
        mockMvc.perform(get("/api/v1/applications/{id}", applicationId).header("Authorization", user.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("OFFER"));
        mockMvc.perform(post("/api/v1/applications/{id}/offer", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        updateOffer(user, applicationId, "ACCEPTED");
        mockMvc.perform(get("/api/v1/applications/{id}", applicationId).header("Authorization", user.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("ENDED"))
                .andExpect(jsonPath("$.endReason").value("OFFER_ACCEPTED"));
        mockMvc.perform(put("/api/v1/applications/{id}/offer", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));

        mockMvc.perform(put("/api/v1/applications/{id}/final-review", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("第一次复盘")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary").value("第一次复盘"));
        mockMvc.perform(put("/api/v1/applications/{id}/final-review", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("更新后的复盘")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary").value("更新后的复盘"));
        Integer reviewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM final_review WHERE application_id = ?", Integer.class, applicationId);
        assertThat(reviewCount).isEqualTo(1);

        Long rejectedApplicationId = createApplication(user, jobId, resume.versionId());
        createOffer(user, rejectedApplicationId);
        updateOffer(user, rejectedApplicationId, "REJECTED");
        mockMvc.perform(get("/api/v1/applications/{id}", rejectedApplicationId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("ENDED"))
                .andExpect(jsonPath("$.endReason").value("OFFER_REJECTED"));
    }

    @Test
    void jobDeleteAllowsNoHistoryButRejectsAnyApplicationHistory() throws Exception {
        AuthSession user = registerAndLogin();
        Long companyId = createCompany(user, "删除语义公司");
        Long cleanJobId = createJob(user, companyId, "无历史岗位");
        mockMvc.perform(post("/api/v1/jobs/{id}/notes", cleanJobId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"随岗位一并清理的过程笔记\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/jobs/{id}", cleanJobId).header("Authorization", user.authorization()))
                .andExpect(status().isNoContent());
        Integer remainingNotes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_note WHERE job_id = ?", Integer.class, cleanJobId);
        assertThat(remainingNotes).isZero();

        Long usedJobId = createJob(user, companyId, "有历史岗位");
        Long applicationId = createApplication(user, usedJobId, createResumeVersion(user, true).versionId());
        mockMvc.perform(delete("/api/v1/jobs/{id}", usedJobId).header("Authorization", user.authorization()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
        transition(user, applicationId, "ENDED", "NO_LONGER_INTERESTED", "主动结束");
        mockMvc.perform(delete("/api/v1/jobs/{id}", usedJobId).header("Authorization", user.authorization()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    @Test
    void everyApplicationResourceIsOwnerIsolated() throws Exception {
        AuthSession owner = registerAndLogin();
        AuthSession attacker = registerAndLogin();
        Long companyId = createCompany(owner, "隔离公司");
        Long applicationId = createApplication(owner, createJob(owner, companyId, "隔离岗位"),
                createResumeVersion(owner, true).versionId());
        long assessmentId = idFrom(mockMvc.perform(post("/api/v1/applications/{id}/assessments", applicationId)
                .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"OTHER\",\"title\":\"测评\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long interviewId = idFrom(mockMvc.perform(post("/api/v1/applications/{id}/interviews", applicationId)
                .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundNo\":1,\"type\":\"HR\",\"title\":\"HR 面\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        createOffer(owner, applicationId);
        updateOffer(owner, applicationId, "ACCEPTED");
        mockMvc.perform(put("/api/v1/applications/{id}/final-review", applicationId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("隔离复盘"))).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/{id}", applicationId).header("Authorization", attacker.authorization())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/applications/{id}/transitions", applicationId)
                        .header("Authorization", attacker.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStage\":\"ENDED\",\"endReason\":\"OTHER\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/applications/{id}/history", applicationId).header("Authorization", attacker.authorization())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/applications/{id}/assessments/{child}", applicationId, assessmentId).header("Authorization", attacker.authorization())).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/applications/{id}/assessments/{child}", applicationId, assessmentId)
                        .header("Authorization", attacker.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"title\":\"越权修改\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/applications/{id}/interviews/{child}", applicationId, interviewId).header("Authorization", attacker.authorization())).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/applications/{id}/interviews/{child}", applicationId, interviewId)
                        .header("Authorization", attacker.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundNo\":1,\"type\":\"HR\",\"title\":\"越权修改\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/applications/{id}/offer", applicationId).header("Authorization", attacker.authorization())).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/applications/{id}/offer", applicationId)
                        .header("Authorization", attacker.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/applications/{id}/final-review", applicationId).header("Authorization", attacker.authorization())).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/applications/{id}/final-review", applicationId)
                        .header("Authorization", attacker.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("越权复盘"))).andExpect(status().isNotFound());
    }

    private AuthSession registerAndLogin() throws Exception {
        String username = "app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 22);
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

    private Long createCompany(AuthSession user, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/companies").header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return idFrom(body);
    }

    private Long createJob(AuthSession user, Long companyId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/jobs").header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":" + companyId + ",\"title\":\"" + title
                                + "\",\"city\":\"上海\",\"jobType\":\"FULL_TIME\","
                                + "\"rawJd\":\"负责 Java 服务开发\",\"sourceType\":\"MANUAL\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return idFrom(body);
    }

    private ResumeFixture createResumeVersion(AuthSession user, boolean finalize) throws Exception {
        String resumeBody = mockMvc.perform(post("/api/v1/resumes").header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"求职简历\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long resumeId = idFrom(resumeBody);
        String versionBody = mockMvc.perform(post("/api/v1/resumes/{id}/versions", resumeId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long versionId = idFrom(versionBody);
        if (finalize) {
            mockMvc.perform(post("/api/v1/resumes/{resumeId}/versions/{versionId}/finalize", resumeId, versionId)
                            .header("Authorization", user.authorization()))
                    .andExpect(status().isOk());
        }
        return new ResumeFixture(resumeId, versionId);
    }

    private Long createApplication(AuthSession user, Long jobId, Long versionId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/applications").header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(applicationJson(jobId, versionId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return idFrom(body);
    }

    private void transition(AuthSession user, Long applicationId, String stage, String reason, String note) throws Exception {
        String reasonJson = reason == null ? "" : ",\"endReason\":\"" + reason + "\"";
        mockMvc.perform(post("/api/v1/applications/{id}/transitions", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStage\":\"" + stage + "\"" + reasonJson
                                + ",\"note\":\"" + note + "\"}"))
                .andExpect(status().isOk());
    }

    private void createOffer(AuthSession user, Long applicationId) throws Exception {
        mockMvc.perform(post("/api/v1/applications/{id}/offer", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionTitle\":\"高级工程师\",\"compensation\":\"30k\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONSIDERING"));
    }

    private void updateOffer(AuthSession user, Long applicationId, String statusValue) throws Exception {
        mockMvc.perform(put("/api/v1/applications/{id}/offer", applicationId)
                        .header("Authorization", user.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + statusValue + "\",\"positionTitle\":\"高级工程师\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(statusValue));
    }

    private int historyCount(Long applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_stage_history WHERE application_id = ?",
                Integer.class, applicationId);
    }

    private long idFrom(String json) throws Exception {
        return objectMapper.readTree(json).get("id").asLong();
    }

    private String applicationJson(Long jobId, Long versionId) {
        return "{\"jobId\":" + jobId + ",\"resumeVersionId\":" + versionId + "}";
    }

    private String reviewJson(String summary) {
        return "{\"summary\":\"" + summary + "\",\"rating\":5,\"reviewedAt\":\"2026-09-04T10:00:00\"}";
    }

    private record AuthSession(String token, Long userId) {
        String authorization() { return "Bearer " + token; }
    }

    private record ResumeFixture(Long resumeId, Long versionId) { }
}
