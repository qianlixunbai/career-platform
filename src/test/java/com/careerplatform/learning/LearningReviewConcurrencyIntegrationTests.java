package com.careerplatform.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerplatform.learning.dto.WeeklyReviewRequest;
import com.careerplatform.learning.entity.WeeklyReview;
import com.careerplatform.learning.mapper.WeeklyReviewMapper;
import com.careerplatform.learning.service.LearningService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LearningReviewConcurrencyIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LearningService learningService;

    @Autowired
    private WeeklyReviewMapper weeklyReviewMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AuthSession owner;
    private Long planId;

    @AfterEach
    void cleanUp() {
        if (planId != null && owner != null) {
            try {
                learningService.deletePlan(planId, owner.userId());
            } catch (RuntimeException ignored) {
                // Direct cleanup below is the fallback if the service assertion failed.
            }
        }
        if (planId != null) {
            jdbcTemplate.update("DELETE FROM learning_material WHERE plan_id = ?", planId);
            jdbcTemplate.update("DELETE FROM learning_note WHERE plan_id = ?", planId);
            jdbcTemplate.update("DELETE FROM weekly_review WHERE plan_id = ?", planId);
            jdbcTemplate.update("DELETE sr FROM study_record sr "
                    + "JOIN learning_task lt ON lt.id = sr.task_id WHERE lt.plan_id = ?", planId);
            jdbcTemplate.update("DELETE FROM learning_task WHERE plan_id = ?", planId);
            jdbcTemplate.update("DELETE FROM learning_plan WHERE id = ?", planId);
        }
        if (owner != null) {
            appUserMapper.deleteById(owner.userId());
        }
    }

    @Test
    void concurrentFirstReviewPutsShouldBothSucceedAndKeepOneReviewRow() throws Exception {
        owner = registerAndLogin("learning_concurrent_");
        planId = createPlan(owner, "2027-11-01", "2027-11-07", "Concurrent review plan", "IN_PROGRESS");

        CyclicBarrier startGate = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<WeeklyReview> first = executor.submit(() -> upsertAfterGate(startGate,
                    reviewRequest("First concurrent summary", "2027-11-07T10:00:00")));
            Future<WeeklyReview> second = executor.submit(() -> upsertAfterGate(startGate,
                    reviewRequest("Second concurrent summary", "2027-11-07T11:00:00")));

            WeeklyReview firstResult = first.get(30, TimeUnit.SECONDS);
            WeeklyReview secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(firstResult.getId()).isNotNull();
            assertThat(secondResult.getId()).isEqualTo(firstResult.getId());
            assertThat(weeklyReviewMapper.selectCount(new LambdaQueryWrapper<WeeklyReview>()
                    .eq(WeeklyReview::getPlanId, planId)
                    .eq(WeeklyReview::getUserId, owner.userId()))).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private WeeklyReview upsertAfterGate(CyclicBarrier startGate, WeeklyReviewRequest request) throws Exception {
        startGate.await(30, TimeUnit.SECONDS);
        return learningService.upsertReview(planId, owner.userId(), request);
    }

    private Long createPlan(AuthSession user, String weekStart, String weekEnd,
                            String mainGoal, String status) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(weekStart, weekEnd, mainGoal, status)))
                .andExpect(status().isCreated());
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
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
        JsonNode response = objectMapper.readTree(login);
        return new AuthSession(response.get("token").asText(), response.get("userId").asLong());
    }

    private WeeklyReviewRequest reviewRequest(String summary, String reviewedAt) {
        WeeklyReviewRequest request = new WeeklyReviewRequest();
        request.setSummary(summary);
        request.setReviewedAt(LocalDateTime.parse(reviewedAt));
        return request;
    }

    private String planJson(String weekStart, String weekEnd, String mainGoal, String status) {
        return "{\"weekStart\":\"%s\",\"weekEnd\":\"%s\",\"mainGoal\":\"%s\",\"status\":\"%s\"}"
                .formatted(weekStart, weekEnd, mainGoal, status);
    }

    private record AuthSession(String token, Long userId) {
        String authorization() {
            return "Bearer " + token;
        }
    }
}
