package com.careerplatform.learning;

import com.careerplatform.common.exception.DuplicateResourceException;
import com.careerplatform.learning.dto.LearningPlanRequest;
import com.careerplatform.learning.dto.LearningTaskRequest;
import com.careerplatform.learning.enums.LearningPlanStatus;
import com.careerplatform.learning.enums.LearningTaskStatus;
import com.careerplatform.learning.mapper.LearningTaskMapper;
import com.careerplatform.learning.service.LearningService;
import com.careerplatform.user.entity.AppUser;
import com.careerplatform.user.service.UserService;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mockingDetails;

/**
 * Real-MySQL checks for the deterministic plan-confirm transaction boundary.
 *
 * <p>These tests deliberately have no test-level {@code @Transactional}; each
 * service invocation must commit or roll back its own transaction before the
 * independent JdbcTemplate assertions can observe the result.</p>
 */
@SpringBootTest
@Import(LearningServiceAiConfirmTransactionIntegrationTests.FailureInjectionConfiguration.class)
class LearningServiceAiConfirmTransactionIntegrationTests {

    @Autowired
    private LearningService learningService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskInsertFailureInterceptor failureInjector;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private LearningTaskMapper learningTaskMapper;

    private Long cleanupUserId;

    @Test
    void failureInjectionIsInstalledOnRealMapperPipeline() {
        assertThat(mockingDetails(learningTaskMapper).isMock()).isFalse();
        assertThat(sqlSessionFactory.getConfiguration().getInterceptors()).contains(failureInjector);
        assertThat(sqlSessionFactory.getConfiguration()
                .getMappedStatement(LearningTaskMapper.class.getName() + ".insert")
                .getSqlCommandType()).isEqualTo(SqlCommandType.INSERT);
    }

    @AfterEach
    void cleanUpTestOwner() {
        failureInjector.disarm();
        if (cleanupUserId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM learning_task WHERE user_id = ?", cleanupUserId);
        jdbcTemplate.update("DELETE FROM learning_plan WHERE user_id = ?", cleanupUserId);
        jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", cleanupUserId);
        cleanupUserId = null;
    }

    @Test
    void confirmedBundleCommitsAndDuplicateWeekIsRejected() {
        assertNoOuterTransaction();
        Long userId = createTestUser();
        LearningPlanRequest plan = plan("2031-01-06", "2031-01-12", "Commit a confirmed plan");

        LearningService.CreatedPlanWithTasks created = learningService.createPlanWithTasks(
                userId, plan, List.of(
                        task("Implement the exercise", 90, "2031-01-08", 0),
                        task("Write the review", 60, "2031-01-11", 1)), 180);

        assertThat(created.plan().getId()).isPositive();
        assertThat(created.tasks()).hasSize(2);
        assertThat(count("SELECT COUNT(*) FROM learning_plan WHERE id = ? AND user_id = ?",
                created.plan().getId(), userId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM learning_task WHERE plan_id = ? AND user_id = ?",
                created.plan().getId(), userId)).isEqualTo(2);

        assertThatThrownBy(() -> learningService.createPlanWithTasks(
                userId, plan, List.of(task("A second plan", 30, "2031-01-07", 0)), 60))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("同一用户同一周起始日期只能创建一个学习计划");
        assertThat(count("SELECT COUNT(*) FROM learning_plan WHERE user_id = ? AND week_start = ?",
                userId, plan.getWeekStart())).isEqualTo(1);
    }

    @Test
    void secondTaskInsertFailureRollsBackPlanAndFirstTask() {
        assertNoOuterTransaction();
        Long userId = createTestUser();
        LearningPlanRequest plan = plan("2031-02-03", "2031-02-09", "Rollback a partial confirmation");
        InsertProbe probe = failureInjector.arm(userId);

        Throwable thrown = catchThrowable(() -> learningService.createPlanWithTasks(
                userId, plan, List.of(
                        task("First task", 60, "2031-02-04", 0),
                        task("Second task", 60, "2031-02-05", 1)), 180));

        // MyBatis/Spring can wrap an Executor exception. Require the exact
        // injected cause, not an unrelated exception from the test mechanism.
        assertThat(thrown).isInstanceOf(RuntimeException.class);
        Throwable rootCause = thrown;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertThat(rootCause).isSameAs(probe.failure);
        assertThat(probe.insertCalls).isEqualTo(2);
        assertThat(probe.firstTaskActuallyInserted).isTrue();
        assertThat(probe.planId).isPositive();
        assertThat(probe.taskId).isPositive();
        assertNoOuterTransaction();
        assertThat(count("SELECT COUNT(*) FROM learning_plan WHERE id = ?", probe.planId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM learning_task WHERE id = ?", probe.taskId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM learning_plan WHERE user_id = ? AND week_start = ?",
                userId, plan.getWeekStart())).isZero();
        assertThat(count("SELECT COUNT(*) FROM learning_task WHERE user_id = ? AND title IN (?, ?)",
                userId, "First task", "Second task")).isZero();
    }

    private void assertNoOuterTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("confirm transaction test must not inherit a test-level transaction")
                .isFalse();
    }

    private Long createTestUser() {
        AppUser user = userService.register(
                "confirm_tx_" + UUID.randomUUID().toString().replace("-", ""),
                "correct-password");
        cleanupUserId = user.getId();
        return user.getId();
    }

    private LearningPlanRequest plan(String weekStart, String weekEnd, String mainGoal) {
        LearningPlanRequest request = new LearningPlanRequest();
        request.setWeekStart(LocalDate.parse(weekStart));
        request.setWeekEnd(LocalDate.parse(weekEnd));
        request.setMainGoal(mainGoal);
        request.setStatus(LearningPlanStatus.PLANNED);
        return request;
    }

    private LearningTaskRequest task(String title, int plannedMinutes, String dueDate, int sortOrder) {
        LearningTaskRequest request = new LearningTaskRequest();
        request.setTitle(title);
        request.setDescription("Deterministic transaction test task");
        request.setStatus(LearningTaskStatus.TODO);
        request.setPlannedMinutes(plannedMinutes);
        request.setDueDate(LocalDate.parse(dueDate));
        request.setSortOrder(sortOrder);
        return request;
    }

    private int count(String sql, Object... arguments) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return result == null ? 0 : result;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureInjectionConfiguration {
        @Bean
        TaskInsertFailureInterceptor taskInsertFailureInterceptor(JdbcTemplate jdbcTemplate) {
            return new TaskInsertFailureInterceptor(jdbcTemplate);
        }
    }

    /** Only this test context installs the interceptor; other threads pass through. */
    @Intercepts(@Signature(type = Executor.class, method = "update",
            args = {MappedStatement.class, Object.class}))
    static class TaskInsertFailureInterceptor implements Interceptor {
        private final JdbcTemplate jdbcTemplate;
        private final ThreadLocal<InsertProbe> activeProbe = new ThreadLocal<>();

        TaskInsertFailureInterceptor(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        InsertProbe arm(Long userId) {
            InsertProbe probe = new InsertProbe(userId);
            activeProbe.set(probe);
            return probe;
        }

        void disarm() {
            activeProbe.remove();
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            InsertProbe probe = activeProbe.get();
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            if (probe == null || !statement.getId().equals(LearningTaskMapper.class.getName() + ".insert")) {
                return invocation.proceed();
            }

            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            Connection connection = ((Executor) invocation.getTarget()).getTransaction().getConnection();
            assertThat(DataSourceUtils.isConnectionTransactional(connection, jdbcTemplate.getDataSource()))
                    .as("MyBatis INSERT and JdbcTemplate observations must use the Spring-bound transaction")
                    .isTrue();
            if (++probe.insertCalls == 2) {
                assertThat(connection).isSameAs(probe.transactionConnection);
                assertThat(probe.firstTaskActuallyInserted).isTrue();
                throw probe.failure;
            }

            probe.transactionConnection = connection;
            Object result = invocation.proceed(); // Execute real MyBatis SQL, never a Mockito real-method call.
            assertThat(result).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM learning_plan WHERE user_id = ?", Long.class, probe.userId))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM learning_task WHERE user_id = ?", Long.class, probe.userId))
                    .isEqualTo(1L);
            probe.planId = jdbcTemplate.queryForObject(
                    "SELECT id FROM learning_plan WHERE user_id = ?", Long.class, probe.userId);
            probe.taskId = jdbcTemplate.queryForObject(
                    "SELECT id FROM learning_task WHERE plan_id = ? AND user_id = ?",
                    Long.class, probe.planId, probe.userId);
            probe.firstTaskActuallyInserted = true;
            return result;
        }
    }

    static class InsertProbe {
        final Long userId;
        final IllegalStateException failure = new IllegalStateException("synthetic second task insert failure");
        int insertCalls;
        boolean firstTaskActuallyInserted;
        Long planId;
        Long taskId;
        Connection transactionConnection;

        InsertProbe(Long userId) {
            this.userId = userId;
        }
    }
}
