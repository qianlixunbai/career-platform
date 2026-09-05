package com.careerplatform.learning.service;

import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.learning.dto.LearningPlanRequest;
import com.careerplatform.learning.dto.LearningTaskRequest;
import com.careerplatform.learning.entity.LearningPlan;
import com.careerplatform.learning.entity.LearningTask;
import com.careerplatform.learning.enums.LearningPlanStatus;
import com.careerplatform.learning.enums.LearningTaskStatus;
import com.careerplatform.learning.mapper.LearningMaterialMapper;
import com.careerplatform.learning.mapper.LearningNoteMapper;
import com.careerplatform.learning.mapper.LearningPlanMapper;
import com.careerplatform.learning.mapper.LearningTaskMapper;
import com.careerplatform.learning.mapper.StudyRecordMapper;
import com.careerplatform.learning.mapper.WeeklyReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningServiceAiConfirmTest {

    @Mock private LearningPlanMapper learningPlanMapper;
    @Mock private LearningTaskMapper learningTaskMapper;
    @Mock private StudyRecordMapper studyRecordMapper;
    @Mock private LearningNoteMapper learningNoteMapper;
    @Mock private LearningMaterialMapper learningMaterialMapper;
    @Mock private WeeklyReviewMapper weeklyReviewMapper;

    private LearningService learningService;

    @BeforeEach
    void setUp() {
        learningService = new LearningService(
                learningPlanMapper,
                learningTaskMapper,
                studyRecordMapper,
                learningNoteMapper,
                learningMaterialMapper,
                weeklyReviewMapper);
    }

    @Test
    void createsOnePlanAndAllConfirmedTasksThroughOneDeterministicBoundary() {
        LearningPlanRequest planRequest = planRequest();
        List<LearningTaskRequest> tasks = List.of(
                task("Redis cache-aside 练习", 90, "2028-04-02", 0),
                task("Redis 故障场景复盘", 60, "2028-04-05", 1));
        when(learningPlanMapper.selectCount(any())).thenReturn(0L);
        org.mockito.Mockito.doAnswer(invocation -> {
            LearningPlan plan = invocation.getArgument(0);
            plan.setId(41L);
            return 1;
        }).when(learningPlanMapper).insert(any(LearningPlan.class));
        LearningPlan storedPlan = new LearningPlan();
        storedPlan.setId(41L);
        storedPlan.setUserId(9L);
        storedPlan.setWeekStart(planRequest.getWeekStart());
        storedPlan.setWeekEnd(planRequest.getWeekEnd());
        storedPlan.setMainGoal(planRequest.getMainGoal());
        storedPlan.setStatus(LearningPlanStatus.PLANNED);
        when(learningPlanMapper.selectOne(any())).thenReturn(storedPlan);
        when(learningTaskMapper.selectList(any())).thenAnswer(invocation -> List.of());

        LearningService.CreatedPlanWithTasks result =
                learningService.createPlanWithTasks(9L, planRequest, tasks, 180);

        assertThat(result.plan().getId()).isEqualTo(41L);
        verify(learningPlanMapper).insert(any(LearningPlan.class));
        verify(learningTaskMapper, org.mockito.Mockito.times(2)).insert(any(LearningTask.class));
    }

    @Test
    void validatesEveryTaskBeforeWritingAnything() {
        List<LearningTaskRequest> tasks = List.of(
                task("Redis 练习", 60, "2028-04-02", 0),
                task("redis 练习", 60, "2028-04-03", 1));

        assertThatThrownBy(() -> learningService.createPlanWithTasks(9L, planRequest(), tasks, 180))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("学习任务标题不能重复");

        verify(learningPlanMapper, never()).insert(any(LearningPlan.class));
        verify(learningTaskMapper, never()).insert(any(LearningTask.class));
    }

    @Test
    void rejectsTasksThatExceedTheUserTimeBudgetBeforeWriting() {
        List<LearningTaskRequest> tasks = List.of(
                task("Redis 练习", 120, "2028-04-02", 0),
                task("MySQL 索引练习", 90, "2028-04-03", 1));

        assertThatThrownBy(() -> learningService.createPlanWithTasks(9L, planRequest(), tasks, 180))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("任务总计划用时不能超过每周可用时间");

        verify(learningPlanMapper, never()).insert(any(LearningPlan.class));
        verify(learningTaskMapper, never()).insert(any(LearningTask.class));
    }

    @Test
    void rejectsDueDateOutsideTheConfirmedPlanBeforeWriting() {
        List<LearningTaskRequest> tasks = List.of(task("Redis 练习", 60, "2028-04-08", 0));

        assertThatThrownBy(() -> learningService.createPlanWithTasks(9L, planRequest(), tasks, 180))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("任务截止日期必须在计划日期范围内");

        verify(learningPlanMapper, never()).insert(any(LearningPlan.class));
        verify(learningTaskMapper, never()).insert(any(LearningTask.class));
    }

    private LearningPlanRequest planRequest() {
        LearningPlanRequest request = new LearningPlanRequest();
        request.setWeekStart(LocalDate.parse("2028-04-01"));
        request.setWeekEnd(LocalDate.parse("2028-04-07"));
        request.setMainGoal("聚焦 Redis 可靠性实践");
        request.setStatus(LearningPlanStatus.PLANNED);
        return request;
    }

    private LearningTaskRequest task(String title, int minutes, String dueDate, int sortOrder) {
        LearningTaskRequest request = new LearningTaskRequest();
        request.setTitle(title);
        request.setDescription("完成可验收的编码与笔记产出");
        request.setStatus(LearningTaskStatus.TODO);
        request.setPlannedMinutes(minutes);
        request.setDueDate(LocalDate.parse(dueDate));
        request.setSortOrder(sortOrder);
        return request;
    }
}
