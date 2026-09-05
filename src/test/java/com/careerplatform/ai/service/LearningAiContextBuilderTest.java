package com.careerplatform.ai.service;

import com.careerplatform.ai.dto.learning.LearningPlanAiSuggestionRequest;
import com.careerplatform.ai.dto.learning.LearningAiEvidence;
import com.careerplatform.career.service.CareerService;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.learning.entity.LearningPlan;
import com.careerplatform.learning.entity.LearningTask;
import com.careerplatform.learning.entity.StudyRecord;
import com.careerplatform.learning.mapper.LearningNoteMapper;
import com.careerplatform.learning.mapper.LearningPlanMapper;
import com.careerplatform.learning.mapper.LearningTaskMapper;
import com.careerplatform.learning.mapper.StudyRecordMapper;
import com.careerplatform.learning.mapper.WeeklyReviewMapper;
import com.careerplatform.learning.enums.LearningTaskStatus;
import com.careerplatform.profile.entity.Skill;
import com.careerplatform.profile.entity.UserSkill;
import com.careerplatform.profile.enums.Proficiency;
import com.careerplatform.profile.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningAiContextBuilderTest {
    private static final Long USER_ID = 7L;

    @Mock private CareerService careerService;
    @Mock private ProfileService profileService;
    @Mock private LearningPlanMapper planMapper;
    @Mock private LearningTaskMapper taskMapper;
    @Mock private StudyRecordMapper recordMapper;
    @Mock private WeeklyReviewMapper reviewMapper;
    @Mock private LearningNoteMapper noteMapper;

    private LearningAiContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new LearningAiContextBuilder(
                careerService, profileService, planMapper, taskMapper, recordMapper, reviewMapper, noteMapper);
    }

    @Test
    void metricsAggregateStatusesAndActualMinutesPerTaskDeterministically() {
        List<LearningTask> tasks = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            LearningTask task = new LearningTask();
            task.setId((long) index + 1);
            task.setTitle("Task " + index);
            task.setPlannedMinutes(60 + index);
            task.setStatus(index < 4 ? LearningTaskStatus.DONE
                    : index == 4 ? LearningTaskStatus.SKIPPED : LearningTaskStatus.TODO);
            tasks.add(task);
        }
        StudyRecord first = new StudyRecord();
        first.setTaskId(1L);
        first.setDurationMinutes(40);
        StudyRecord second = new StudyRecord();
        second.setTaskId(2L);
        second.setDurationMinutes(70);
        StudyRecord third = new StudyRecord();
        third.setTaskId(2L);
        third.setDurationMinutes(10);

        var metrics = builder.calculateMetrics(tasks, List.of(first, second, third));

        assertThat(metrics.taskCount()).isEqualTo(6);
        assertThat(metrics.doneCount()).isEqualTo(4);
        assertThat(metrics.todoCount()).isEqualTo(1);
        assertThat(metrics.skippedCount()).isEqualTo(1);
        assertThat(metrics.completionRate()).isEqualByComparingTo("66.67");
        assertThat(metrics.plannedMinutes()).isEqualTo(375);
        assertThat(metrics.actualMinutes()).isEqualTo(120);
        assertThat(metrics.studyRecordCount()).isEqualTo(3);
        assertThat(metrics.tasks()).extracting(metric -> metric.actualMinutes())
                .containsExactly(40, 80, 0, 0, 0, 0);
    }

    @Test
    void planContextCapsSkillsAndReturnsExplicitTruncationWarning() {
        List<ProfileService.UserSkillDetail> details = new ArrayList<>();
        for (int index = 0; index < 31; index++) {
            UserSkill userSkill = new UserSkill();
            userSkill.setId((long) index + 1);
            userSkill.setSkillId((long) index + 1);
            userSkill.setProficiency(Proficiency.BEGINNER);
            Skill skill = new Skill();
            skill.setId((long) index + 1);
            skill.setName("Skill " + index);
            details.add(new ProfileService.UserSkillDetail(userSkill, skill));
        }
        when(profileService.listUserSkills(USER_ID)).thenReturn(details);
        when(planMapper.selectList(any())).thenReturn(List.of());
        LearningPlanAiSuggestionRequest request = request();

        var context = builder.buildPlanContext(USER_ID, request);

        assertThat(context.userSkills()).hasSize(30);
        assertThat(context.evidenceByKey().values())
                .filteredOn(item -> item.type() == com.careerplatform.ai.dto.learning.LearningAiEvidenceType.USER_SKILL)
                .hasSize(30);
        assertThat(context.warnings()).contains("部分历史数据因上下文上限未参与本次分析。");
    }

    @Test
    void reviewOwnerIsCheckedBeforeReadingTasksOrCallingAiPath() {
        when(planMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> builder.buildReviewContext(USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskMapper, never()).selectList(any());
        verify(recordMapper, never()).selectList(any());
    }

    private LearningPlanAiSuggestionRequest request() {
        LearningPlanAiSuggestionRequest request = new LearningPlanAiSuggestionRequest();
        request.setWeekStart(LocalDate.of(2026, 9, 7));
        request.setWeekEnd(LocalDate.of(2026, 9, 13));
        request.setAvailableMinutes(300);
        return request;
    }
}
