package com.careerplatform.learning;

import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.learning.dto.LearningPlanRequest;
import com.careerplatform.learning.dto.LearningTaskRequest;
import com.careerplatform.learning.entity.LearningPlan;
import com.careerplatform.learning.entity.LearningTask;
import com.careerplatform.learning.enums.LearningPlanStatus;
import com.careerplatform.learning.enums.LearningTaskStatus;
import com.careerplatform.learning.mapper.LearningPlanMapper;
import com.careerplatform.learning.mapper.LearningTaskMapper;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-MySQL regressions for the parent-plan date-range invariant.
 *
 * <p>Each gate holds an uncommitted parent-row change in a separate
 * TransactionTemplate transaction. The service operation starts while that
 * transaction is still open, so the test does not depend on timing loops or a
 * probabilistic race. The gate is released only after the operation has had a
 * bounded opportunity to reach the parent row.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class LearningPlanTaskRaceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LearningService learningService;

    @Autowired
    private LearningPlanMapper learningPlanMapper;

    @Autowired
    private LearningTaskMapper learningTaskMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    private AuthSession owner;
    private Long planId;
    private Long taskId;

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
    void planShrinkAndTaskCreateMustNotLeaveDueDateOutsidePersistedPlan() throws Exception {
        owner = registerAndLogin("learning_race_create_");
        planId = createPlan(owner, "2028-01-03", "2028-01-09", "Create race plan", "IN_PROGRESS");

        try (PlanRaceGate planGate = holdUncommittedPlanShrink(planId, owner.userId(), "2028-01-05")) {
            ExecutorService workers = Executors.newSingleThreadExecutor();
            try {
                Future<LearningTask> createFuture = workers.submit(() ->
                        learningService.createTask(planId, owner.userId(), taskRequest("2028-01-09")));
                boolean completedBeforePlanCommit = completesWithin(createFuture, 1, TimeUnit.SECONDS);

                planGate.release();
                InvocationResult<LearningTask> createResult = await(createFuture);

                assertThat(createResult.failure())
                        .as("task creation outcome after the parent-plan commit (completed early: %s)",
                                completedBeforePlanCommit)
                        .isInstanceOf(InvalidRequestException.class);
                assertThat(completedBeforePlanCommit)
                        .as("task creation must wait for the in-flight parent-plan change")
                        .isFalse();
            } finally {
                planGate.release();
                workers.shutdownNow();
                assertThat(workers.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }
        }

        assertPersistedTaskDueDatesWithinPlan("2028-01-05");
    }

    @Test
    void planShrinkAndTaskUpdateMustNotLeaveDueDateOutsidePersistedPlan() throws Exception {
        owner = registerAndLogin("learning_race_update_");
        planId = createPlan(owner, "2028-02-07", "2028-02-13", "Update race plan", "IN_PROGRESS");
        taskId = createTask(null);

        try (PlanRaceGate planGate = holdUncommittedPlanShrink(planId, owner.userId(), "2028-02-10")) {
            ExecutorService workers = Executors.newSingleThreadExecutor();
            try {
                Future<LearningTask> updateFuture = workers.submit(() ->
                        learningService.updateTask(planId, taskId, owner.userId(), taskRequest("2028-02-13")));
                boolean completedBeforePlanCommit = completesWithin(updateFuture, 1, TimeUnit.SECONDS);

                planGate.release();
                InvocationResult<LearningTask> updateResult = await(updateFuture);

                assertThat(updateResult.failure())
                        .as("task update outcome after the parent-plan commit (completed early: %s)",
                                completedBeforePlanCommit)
                        .isInstanceOf(InvalidRequestException.class);
                assertThat(completedBeforePlanCommit)
                        .as("task update must wait for the in-flight parent-plan change")
                        .isFalse();
            } finally {
                planGate.release();
                workers.shutdownNow();
                assertThat(workers.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }
        }

        assertPersistedTaskDueDatesWithinPlan("2028-02-10");
        assertThat(learningTaskMapper.selectById(taskId).getDueDate()).isNull();
    }

    @Test
    void planShrinkMustRecheckTaskInsertedBehindItsParentLock() throws Exception {
        owner = registerAndLogin("learning_race_plan_");
        planId = createPlan(owner, "2028-03-01", "2028-03-09", "Plan race plan", "IN_PROGRESS");

        try (PlanRaceGate taskGate = holdUncommittedTaskInsert(
                planId, owner.userId(), LocalDate.parse("2028-03-09"))) {
            ExecutorService workers = Executors.newSingleThreadExecutor();
            try {
                Future<LearningPlan> shrinkFuture = workers.submit(() ->
                        learningService.updatePlan(planId, owner.userId(),
                                planRequest("2028-03-01", "2028-03-05")));
                boolean completedBeforeTaskCommit = completesWithin(shrinkFuture, 1, TimeUnit.SECONDS);

                taskGate.release();
                InvocationResult<LearningPlan> shrinkResult = await(shrinkFuture);

                assertThat(shrinkResult.failure())
                        .as("plan update outcome after the child-task commit (completed early: %s)",
                                completedBeforeTaskCommit)
                        .isInstanceOf(InvalidRequestException.class);
                assertThat(completedBeforeTaskCommit)
                        .as("plan update must wait for the in-flight child-task insert")
                        .isFalse();
            } finally {
                taskGate.release();
                workers.shutdownNow();
                assertThat(workers.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }
        }

        assertPersistedTaskDueDatesWithinPlan("2028-03-09");
    }

    private void assertPersistedTaskDueDatesWithinPlan(String expectedWeekEnd) {
        LearningPlan persistedPlan = learningPlanMapper.selectById(planId);
        Integer outsideRangeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_task task "
                        + "JOIN learning_plan plan ON plan.id = task.plan_id "
                        + "WHERE task.plan_id = ? AND task.user_id = plan.user_id "
                        + "AND task.due_date IS NOT NULL "
                        + "AND (task.due_date < plan.week_start OR task.due_date > plan.week_end)",
                Integer.class, planId);
        assertThat(persistedPlan).isNotNull();
        assertThat(persistedPlan.getWeekEnd()).isEqualTo(LocalDate.parse(expectedWeekEnd));
        assertThat(outsideRangeCount).isZero();
    }

    private PlanRaceGate holdUncommittedPlanShrink(Long currentPlanId, Long userId,
                                                    String newWeekEnd) {
        return PlanRaceGate.start(transactionManager, () -> {
            lockPlan(currentPlanId, userId);
            jdbcTemplate.update("UPDATE learning_plan SET week_end = ? WHERE id = ? AND user_id = ?",
                    LocalDate.parse(newWeekEnd), currentPlanId, userId);
        });
    }

    private PlanRaceGate holdUncommittedTaskInsert(Long currentPlanId, Long userId,
                                                   LocalDate dueDate) {
        return PlanRaceGate.start(transactionManager, () -> {
            lockPlan(currentPlanId, userId);
            jdbcTemplate.update("INSERT INTO learning_task "
                            + "(user_id, plan_id, title, status, planned_minutes, due_date, sort_order) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    userId, currentPlanId, "Uncommitted race task", LearningTaskStatus.TODO.name(),
                    30, dueDate, 0);
            taskId = jdbcTemplate.queryForObject(
                    "SELECT id FROM learning_task WHERE plan_id = ? AND user_id = ? "
                            + "AND title = ? ORDER BY id DESC LIMIT 1",
                    Long.class, currentPlanId, userId, "Uncommitted race task");
        });
    }

    private void lockPlan(Long currentPlanId, Long userId) {
        Long lockedPlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM learning_plan WHERE id = ? AND user_id = ? FOR UPDATE",
                Long.class, currentPlanId, userId);
        if (lockedPlanId == null) {
            throw new IllegalStateException("Race test plan disappeared");
        }
    }

    private boolean completesWithin(Future<?> future, long timeout, TimeUnit unit) throws Exception {
        try {
            future.get(timeout, unit);
            return true;
        } catch (TimeoutException exception) {
            return false;
        } catch (ExecutionException exception) {
            return true;
        }
    }

    private <T> InvocationResult<T> await(Future<T> future) throws Exception {
        try {
            return new InvocationResult<>(future.get(30, TimeUnit.SECONDS), null);
        } catch (ExecutionException exception) {
            return new InvocationResult<>(null, exception.getCause());
        } catch (TimeoutException exception) {
            throw new AssertionError("Concurrent operation did not finish", exception);
        }
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

    private Long createTask(String dueDate) {
        LearningTask task = learningService.createTask(planId, owner.userId(), taskRequest(dueDate));
        taskId = task.getId();
        return taskId;
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

    private LearningPlanRequest planRequest(String weekStart, String weekEnd) {
        LearningPlanRequest request = new LearningPlanRequest();
        request.setWeekStart(LocalDate.parse(weekStart));
        request.setWeekEnd(LocalDate.parse(weekEnd));
        request.setMainGoal("Race plan update");
        request.setStatus(LearningPlanStatus.IN_PROGRESS);
        return request;
    }

    private LearningTaskRequest taskRequest(String dueDate) {
        LearningTaskRequest request = new LearningTaskRequest();
        request.setTitle("Race task");
        request.setStatus(LearningTaskStatus.TODO);
        request.setPlannedMinutes(30);
        request.setDueDate(dueDate == null ? null : LocalDate.parse(dueDate));
        request.setSortOrder(0);
        return request;
    }

    private String planJson(String weekStart, String weekEnd, String mainGoal, String status) {
        return "{\"weekStart\":\"%s\",\"weekEnd\":\"%s\",\"mainGoal\":\"%s\",\"status\":\"%s\"}"
                .formatted(weekStart, weekEnd, mainGoal, status);
    }

    private record InvocationResult<T>(T value, Throwable failure) {
    }

    private record AuthSession(String token, Long userId) {
        String authorization() {
            return "Bearer " + token;
        }
    }

    private static final class PlanRaceGate implements AutoCloseable {

        private final CountDownLatch actionFinished = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<?> transactionFuture;

        private PlanRaceGate(DataSourceTransactionManager transactionManager, Runnable action) {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                action.run();
                actionFinished.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test transaction gate was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Test transaction gate interrupted", exception);
                }
            }));
        }

        static PlanRaceGate start(DataSourceTransactionManager transactionManager, Runnable action) {
            PlanRaceGate gate = new PlanRaceGate(transactionManager, action);
            try {
                assertThat(gate.actionFinished.await(30, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Test transaction gate interrupted", exception);
            }
            return gate;
        }

        void release() {
            release.countDown();
        }

        @Override
        public void close() {
            release();
            try {
                transactionFuture.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Test transaction gate interrupted", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new AssertionError("Test transaction gate did not finish", exception);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
