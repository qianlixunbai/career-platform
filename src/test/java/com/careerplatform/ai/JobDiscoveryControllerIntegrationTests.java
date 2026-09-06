package com.careerplatform.ai;

import com.careerplatform.ai.client.AiToolCallingGateway;
import com.careerplatform.ai.client.JobSearchGateway;
import com.careerplatform.ai.dto.job.JobDiscoveryAiResult;
import com.careerplatform.career.dto.CareerGoalRequest;
import com.careerplatform.career.dto.CompanyRequest;
import com.careerplatform.career.entity.CareerGoal;
import com.careerplatform.career.entity.Company;
import com.careerplatform.career.service.CareerService;
import com.careerplatform.career.enums.CareerGoalStatus;
import com.careerplatform.user.mapper.AppUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deterministic provider integration against real CareerService SQL. Run this
 * class with the user's committed MySQL test fixtures; no real provider calls
 * are made. The test deliberately avoids an outer rollback transaction so the
 * confirmation REQUIRES_NEW transaction can see committed fixture rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JobDiscoveryControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CareerService careerService;
    @Autowired
    private AppUserMapper appUserMapper;
    @MockitoBean
    private AiToolCallingGateway aiGateway;
    @MockitoBean
    private JobSearchGateway searchGateway;

    private final List<AuthSession> users = new ArrayList<>();
    private final List<Long> goals = new ArrayList<>();
    private final List<Long> jobs = new ArrayList<>();
    private final List<Long> companies = new ArrayList<>();
    private final Map<Long, Long> goalOwners = new LinkedHashMap<>();
    private final Map<Long, Long> jobOwners = new LinkedHashMap<>();
    private final Map<Long, Long> companyOwners = new LinkedHashMap<>();

    @AfterEach
    void cleanUpCommittedFixtures() {
        // Fixtures are intentionally committed because JobDiscoveryService's
        // confirm path starts a REQUIRES_NEW transaction.
        List<org.junit.jupiter.api.function.Executable> cleanup = new ArrayList<>();
        for (int index = jobs.size() - 1; index >= 0; index--) {
            Long jobId = jobs.get(index);
            Long ownerId = jobOwners.get(jobId);
            cleanup.add(() -> careerService.deleteJob(jobId, ownerId));
        }
        for (int index = goals.size() - 1; index >= 0; index--) {
            Long goalId = goals.get(index);
            Long ownerId = goalOwners.get(goalId);
            cleanup.add(() -> careerService.deleteGoal(goalId, ownerId));
        }
        for (int index = companies.size() - 1; index >= 0; index--) {
            Long companyId = companies.get(index);
            Long ownerId = companyOwners.get(companyId);
            cleanup.add(() -> careerService.deleteCompany(companyId, ownerId));
        }
        for (AuthSession user : users) {
            cleanup.add(() -> appUserMapper.deleteById(user.userId()));
        }
        assertAll("job discovery fixture cleanup", cleanup);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deterministicDiscoveryDoesNotWriteBusinessTablesAndConfirmUsesTrustedSource() throws Exception {
        AuthSession owner = registerAndLogin("job_discovery_owner_");
        CareerGoal goal = createGoal(owner);
        Company company = createCompany(owner);
        Map<String, Long> before = businessCounts(owner.userId());

        JobSearchGateway.ProviderSearchResult provider = new JobSearchGateway.ProviderSearchResult(
                "https://careers.example.com/roles/backend-42",
                "Backend Engineer",
                "careers.example.com",
                "A short search snippet that is not a complete JD.",
                null);
        when(searchGateway.search("Backend engineer production backend", "Shanghai", 3))
                .thenReturn(List.of(provider));
        when(aiGateway.discover(anyString(), anyString(), any())).thenAnswer(invocation -> {
            com.careerplatform.ai.tool.JobSearchTool tool = invocation.getArgument(2);
            String resultKey = tool.searchJobs("Backend engineer production backend", "Shanghai", 3)
                    .getFirst().resultKey();
            return new JobDiscoveryAiResult(List.of(new JobDiscoveryAiResult.Advice(
                    resultKey, 1, "Matches the target role.",
                    List.of("Java"), List.of(), List.of("Search snippet is incomplete."), List.of())),
                    List.of());
        });

        String discoveryBody = objectMapper.writeValueAsString(Map.of(
                "careerGoalId", goal.getId(),
                "searchNote", "production backend",
                "maxCandidates", 3));
        String discoveryJson = mockMvc.perform(post("/api/v1/jobs/ai/discovery")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discoveryBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].sourceFacts.sourceUrl")
                        .value(provider.sourceUrl()))
                .andExpect(jsonPath("$.candidates[0].sourceFacts.sourceSnippet")
                        .value(provider.sourceSnippet()))
                .andExpect(jsonPath("$.candidates[0].extractedFields.companyName").doesNotExist())
                .andExpect(jsonPath("$.candidates[0].extractedFields.location").value("Shanghai"))
                .andReturn().getResponse().getContentAsString();
        assertThat(businessCounts(owner.userId())).isEqualTo(before);

        String candidateId = objectMapper.readTree(discoveryJson)
                .path("candidates").get(0).path("candidateId").asText();
        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "candidateId", candidateId,
                "companyId", company.getId(),
                "title", "User confirmed backend role",
                "city", "Shanghai",
                "jobType", "FULL_TIME"));
        mockMvc.perform(post("/api/v1/jobs/ai/discovery/confirm")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").isNumber());

        Long savedJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM job WHERE user_id = ? AND title = ? ORDER BY id DESC LIMIT 1",
                Long.class, owner.userId(), "User confirmed backend role");
        jobs.add(savedJobId);
        jobOwners.put(savedJobId, owner.userId());
        Map<String, Object> saved = jdbcTemplate.queryForMap(
                "SELECT source_type, source_name, source_url, raw_jd FROM job WHERE id = ?", savedJobId);
        assertThat(saved.get("source_type").toString()).isEqualTo("OTHER");
        assertThat(saved.get("source_name")).isEqualTo("careers.example.com");
        assertThat(saved.get("source_url")).isEqualTo(provider.sourceUrl());
        assertThat(saved.get("raw_jd")).isNull();
        verify(aiGateway).discover(anyString(), anyString(), any());
        verify(searchGateway).search("Backend engineer production backend", "Shanghai", 3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void foreignGoalIsNotDisclosedAndDoesNotCallAIOrSearch() throws Exception {
        AuthSession owner = registerAndLogin("job_discovery_owner_");
        AuthSession other = registerAndLogin("job_discovery_other_");
        CareerGoal foreignGoal = createGoal(other);

        mockMvc.perform(post("/api/v1/jobs/ai/discovery")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "careerGoalId", foreignGoal.getId(), "maxCandidates", 3))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        verifyNoInteractions(aiGateway, searchGateway);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentConfirmationsCreateAtMostOneCommittedJob() throws Exception {
        AuthSession owner = registerAndLogin("job_discovery_race_owner_");
        CareerGoal goal = createGoal(owner);
        Company company = createCompany(owner);
        JobSearchGateway.ProviderSearchResult provider = new JobSearchGateway.ProviderSearchResult(
                "https://careers.example.com/roles/backend-race",
                "Backend Engineer",
                "careers.example.com",
                "A deterministic provider snippet.",
                null);
        when(searchGateway.search("Backend engineer production backend", "Shanghai", 3))
                .thenReturn(List.of(provider));
        when(aiGateway.discover(anyString(), anyString(), any())).thenAnswer(invocation -> {
            com.careerplatform.ai.tool.JobSearchTool tool = invocation.getArgument(2);
            String resultKey = tool.searchJobs("Backend engineer production backend", "Shanghai", 3)
                    .getFirst().resultKey();
            return new JobDiscoveryAiResult(List.of(new JobDiscoveryAiResult.Advice(
                    resultKey, 1, "Suitable", List.of(), List.of(), List.of(), List.of())), List.of());
        });

        String discovery = mockMvc.perform(post("/api/v1/jobs/ai/discovery")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "careerGoalId", goal.getId(),
                                "searchNote", "production backend",
                                "maxCandidates", 3))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String candidateId = objectMapper.readTree(discovery).path("candidates").get(0)
                .path("candidateId").asText();
        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "candidateId", candidateId,
                "companyId", company.getId(),
                "title", "Concurrent backend role",
                "city", "Shanghai",
                "jobType", "FULL_TIME"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = List.of();
        try {
            java.util.concurrent.Callable<Integer> confirm = () -> mockMvc.perform(
                            post("/api/v1/jobs/ai/discovery/confirm")
                                    .header("Authorization", owner.authorization())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(confirmBody))
                    .andReturn().getResponse().getStatus();
            futures = executor.invokeAll(List.of(confirm, confirm));
        } finally {
            executor.shutdownNow();
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        Long savedJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM job WHERE user_id = ? AND title = ?",
                Long.class, owner.userId(), "Concurrent backend role");
        assertThat(savedJobId).isNotNull();
        jobs.add(savedJobId);
        jobOwners.put(savedJobId, owner.userId());
        Long jobCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job WHERE user_id = ? AND title = ?",
                Long.class, owner.userId(), "Concurrent backend role");
        assertThat(jobCount).isEqualTo(1L);
        verify(aiGateway).discover(anyString(), anyString(), any());
        verify(searchGateway).search("Backend engineer production backend", "Shanghai", 3);
    }

    private CareerGoal createGoal(AuthSession owner) {
        CareerGoalRequest request = new CareerGoalRequest();
        request.setTargetPosition("Backend engineer");
        request.setTargetCity("Shanghai");
        request.setTargetIndustry("Software");
        request.setStatus(CareerGoalStatus.ACTIVE);
        CareerGoal created = careerService.createGoal(owner.userId(), request);
        goals.add(created.getId());
        goalOwners.put(created.getId(), owner.userId());
        return created;
    }

    private Company createCompany(AuthSession owner) {
        CompanyRequest request = new CompanyRequest();
        request.setName("Discovery Company " + suffix());
        request.setCity("Shanghai");
        Company created = careerService.createCompany(owner.userId(), request);
        companies.add(created.getId());
        companyOwners.put(created.getId(), owner.userId());
        return created;
    }

    private Map<String, Long> businessCounts(Long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("job", count("SELECT COUNT(*) FROM job WHERE user_id = ?", userId));
        counts.put("company", count("SELECT COUNT(*) FROM company WHERE user_id = ?", userId));
        counts.put("job_requirement", count("SELECT COUNT(*) FROM job_requirement jr JOIN job j ON j.id = jr.job_id WHERE j.user_id = ?", userId));
        counts.put("career_goal", count("SELECT COUNT(*) FROM career_goal WHERE user_id = ?", userId));
        counts.put("user_skill", count("SELECT COUNT(*) FROM user_skill WHERE user_id = ?", userId));
        return counts;
    }

    private long count(String sql, Long userId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return value == null ? 0L : value;
    }

    private AuthSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String password = "correct-password";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(login);
        AuthSession session = new AuthSession(response.path("token").asText(), response.path("userId").asLong());
        users.add(session);
        return session;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record AuthSession(String token, Long userId) {
        String authorization() {
            return "Bearer " + token;
        }
    }
}
