package com.careerplatform.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careerplatform.common.exception.DuplicateResourceException;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.learning.dto.LearningMaterialRequest;
import com.careerplatform.learning.dto.LearningNoteRequest;
import com.careerplatform.learning.dto.LearningPlanRequest;
import com.careerplatform.learning.dto.LearningTaskRequest;
import com.careerplatform.learning.dto.StudyRecordRequest;
import com.careerplatform.learning.dto.WeeklyReviewRequest;
import com.careerplatform.learning.entity.LearningMaterial;
import com.careerplatform.learning.entity.LearningNote;
import com.careerplatform.learning.entity.LearningPlan;
import com.careerplatform.learning.entity.LearningTask;
import com.careerplatform.learning.entity.StudyRecord;
import com.careerplatform.learning.entity.WeeklyReview;
import com.careerplatform.learning.enums.LearningPlanStatus;
import com.careerplatform.learning.enums.LearningTaskStatus;
import com.careerplatform.learning.mapper.LearningMaterialMapper;
import com.careerplatform.learning.mapper.LearningNoteMapper;
import com.careerplatform.learning.mapper.LearningPlanMapper;
import com.careerplatform.learning.mapper.LearningTaskMapper;
import com.careerplatform.learning.mapper.StudyRecordMapper;
import com.careerplatform.learning.mapper.WeeklyReviewMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LearningService {

    private final LearningPlanMapper learningPlanMapper;
    private final LearningTaskMapper learningTaskMapper;
    private final StudyRecordMapper studyRecordMapper;
    private final LearningNoteMapper learningNoteMapper;
    private final LearningMaterialMapper learningMaterialMapper;
    private final WeeklyReviewMapper weeklyReviewMapper;

    public LearningService(LearningPlanMapper learningPlanMapper,
                           LearningTaskMapper learningTaskMapper,
                           StudyRecordMapper studyRecordMapper,
                           LearningNoteMapper learningNoteMapper,
                           LearningMaterialMapper learningMaterialMapper,
                           WeeklyReviewMapper weeklyReviewMapper) {
        this.learningPlanMapper = learningPlanMapper;
        this.learningTaskMapper = learningTaskMapper;
        this.studyRecordMapper = studyRecordMapper;
        this.learningNoteMapper = learningNoteMapper;
        this.learningMaterialMapper = learningMaterialMapper;
        this.weeklyReviewMapper = weeklyReviewMapper;
    }

    @Transactional
    public LearningPlan createPlan(Long userId, LearningPlanRequest request) {
        validatePlanDates(request.getWeekStart(), request.getWeekEnd());
        ensurePlanWeekStartAvailable(userId, request.getWeekStart(), null);

        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        apply(plan, request);
        try {
            learningPlanMapper.insert(plan);
        } catch (DuplicateKeyException exception) {
            throw duplicatePlan();
        }
        return requireOwnedPlan(plan.getId(), userId);
    }

    /**
     * Atomically creates one plan and its user-confirmed tasks.
     *
     * <p>This method is provider-neutral: callers must finish any AI work before
     * entering this deterministic persistence boundary.</p>
     */
    @Transactional
    public CreatedPlanWithTasks createPlanWithTasks(
            Long userId,
            LearningPlanRequest planRequest,
            List<LearningTaskRequest> taskRequests,
            int availableMinutes) {
        validatePlanBundle(planRequest, taskRequests, availableMinutes);
        ensurePlanWeekStartAvailable(userId, planRequest.getWeekStart(), null);

        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        apply(plan, planRequest);
        try {
            learningPlanMapper.insert(plan);
        } catch (DuplicateKeyException exception) {
            throw duplicatePlan();
        }

        for (LearningTaskRequest taskRequest : taskRequests) {
            LearningTask task = new LearningTask();
            task.setUserId(userId);
            task.setPlanId(plan.getId());
            apply(task, taskRequest);
            learningTaskMapper.insert(task);
        }
        List<LearningTask> createdTasks = learningTaskMapper.selectList(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getPlanId, plan.getId())
                .eq(LearningTask::getUserId, userId)
                .orderByAsc(LearningTask::getSortOrder)
                .orderByAsc(LearningTask::getId));
        return new CreatedPlanWithTasks(
                requireOwnedPlan(plan.getId(), userId),
                List.copyOf(createdTasks));
    }

    public List<LearningPlan> listPlans(Long userId) {
        return learningPlanMapper.selectList(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getUserId, userId)
                .orderByDesc(LearningPlan::getWeekStart)
                .orderByDesc(LearningPlan::getId));
    }

    public LearningPlan getPlan(Long planId, Long userId) {
        return requireOwnedPlan(planId, userId);
    }

    @Transactional
    public LearningPlan updatePlan(Long planId, Long userId, LearningPlanRequest request) {
        validatePlanDates(request.getWeekStart(), request.getWeekEnd());
        LearningPlan plan = requireOwnedPlanForUpdate(planId, userId);
        ensurePlanWeekStartAvailable(userId, request.getWeekStart(), planId);
        validateTaskDueDatesWithinPlan(planId, userId, request.getWeekStart(), request.getWeekEnd());
        try {
            updateOrNotFound(learningPlanMapper.update(null, new LambdaUpdateWrapper<LearningPlan>()
                    .set(LearningPlan::getWeekStart, request.getWeekStart())
                    .set(LearningPlan::getWeekEnd, request.getWeekEnd())
                    .set(LearningPlan::getMainGoal, request.getMainGoal())
                    .set(LearningPlan::getStatus, request.getStatus())
                    .eq(LearningPlan::getId, planId)
                    .eq(LearningPlan::getUserId, userId)));
        } catch (DuplicateKeyException exception) {
            throw duplicatePlan();
        }
        return requireOwnedPlan(planId, userId);
    }

    @Transactional
    public void deletePlan(Long planId, Long userId) {
        requireOwnedPlan(planId, userId);
        List<LearningTask> tasks = learningTaskMapper.selectList(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getPlanId, planId)
                .eq(LearningTask::getUserId, userId));

        learningMaterialMapper.delete(new LambdaQueryWrapper<com.careerplatform.learning.entity.LearningMaterial>()
                .eq(com.careerplatform.learning.entity.LearningMaterial::getPlanId, planId)
                .eq(com.careerplatform.learning.entity.LearningMaterial::getUserId, userId));
        learningNoteMapper.delete(new LambdaQueryWrapper<com.careerplatform.learning.entity.LearningNote>()
                .eq(com.careerplatform.learning.entity.LearningNote::getPlanId, planId)
                .eq(com.careerplatform.learning.entity.LearningNote::getUserId, userId));
        weeklyReviewMapper.delete(new LambdaQueryWrapper<com.careerplatform.learning.entity.WeeklyReview>()
                .eq(com.careerplatform.learning.entity.WeeklyReview::getPlanId, planId)
                .eq(com.careerplatform.learning.entity.WeeklyReview::getUserId, userId));
        for (LearningTask task : tasks) {
            studyRecordMapper.delete(new LambdaQueryWrapper<StudyRecord>()
                    .eq(StudyRecord::getTaskId, task.getId())
                    .eq(StudyRecord::getUserId, userId));
        }
        learningTaskMapper.delete(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getPlanId, planId)
                .eq(LearningTask::getUserId, userId));
        deleteOrNotFound(learningPlanMapper.delete(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId)));
    }

    @Transactional
    public LearningTask createTask(Long planId, Long userId, LearningTaskRequest request) {
        LearningPlan plan = requireOwnedPlanForUpdate(planId, userId);
        validateTaskDueDate(plan, request.getDueDate());

        LearningTask task = new LearningTask();
        task.setUserId(userId);
        task.setPlanId(planId);
        apply(task, request);
        learningTaskMapper.insert(task);
        return requireOwnedTaskInPlan(planId, task.getId(), userId);
    }

    public List<LearningTask> listTasks(Long planId, Long userId) {
        requireOwnedPlan(planId, userId);
        return learningTaskMapper.selectList(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getPlanId, planId)
                .eq(LearningTask::getUserId, userId)
                .orderByAsc(LearningTask::getSortOrder)
                .orderByAsc(LearningTask::getId));
    }

    public LearningTask getTask(Long planId, Long taskId, Long userId) {
        requireOwnedPlan(planId, userId);
        return requireOwnedTaskInPlan(planId, taskId, userId);
    }

    @Transactional
    public LearningTask updateTask(Long planId, Long taskId, Long userId, LearningTaskRequest request) {
        LearningPlan plan = requireOwnedPlanForUpdate(planId, userId);
        validateTaskDueDate(plan, request.getDueDate());
        LearningTask task = requireOwnedTaskInPlan(planId, taskId, userId);
        updateOrNotFound(learningTaskMapper.update(null, new LambdaUpdateWrapper<LearningTask>()
                .set(LearningTask::getTitle, request.getTitle())
                .set(LearningTask::getDescription, request.getDescription())
                .set(LearningTask::getStatus, request.getStatus())
                .set(LearningTask::getPlannedMinutes, request.getPlannedMinutes())
                .set(LearningTask::getDueDate, request.getDueDate())
                .set(LearningTask::getSortOrder, request.getSortOrder())
                .eq(LearningTask::getId, taskId)
                .eq(LearningTask::getPlanId, planId)
                .eq(LearningTask::getUserId, userId)));
        return requireOwnedTaskInPlan(planId, taskId, userId);
    }

    @Transactional
    public void deleteTask(Long planId, Long taskId, Long userId) {
        requireOwnedPlan(planId, userId);
        requireOwnedTaskInPlan(planId, taskId, userId);

        learningNoteMapper.update(null, new LambdaUpdateWrapper<com.careerplatform.learning.entity.LearningNote>()
                .set(com.careerplatform.learning.entity.LearningNote::getTaskId, null)
                .eq(com.careerplatform.learning.entity.LearningNote::getTaskId, taskId)
                .eq(com.careerplatform.learning.entity.LearningNote::getPlanId, planId)
                .eq(com.careerplatform.learning.entity.LearningNote::getUserId, userId));
        learningMaterialMapper.update(null, new LambdaUpdateWrapper<com.careerplatform.learning.entity.LearningMaterial>()
                .set(com.careerplatform.learning.entity.LearningMaterial::getTaskId, null)
                .eq(com.careerplatform.learning.entity.LearningMaterial::getTaskId, taskId)
                .eq(com.careerplatform.learning.entity.LearningMaterial::getPlanId, planId)
                .eq(com.careerplatform.learning.entity.LearningMaterial::getUserId, userId));
        studyRecordMapper.delete(new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getTaskId, taskId)
                .eq(StudyRecord::getUserId, userId));
        deleteOrNotFound(learningTaskMapper.delete(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getId, taskId)
                .eq(LearningTask::getPlanId, planId)
                .eq(LearningTask::getUserId, userId)));
    }

    @Transactional
    public StudyRecord createRecord(Long taskId, Long userId, StudyRecordRequest request) {
        requireOwnedTask(taskId, userId);
        StudyRecord record = new StudyRecord();
        record.setUserId(userId);
        record.setTaskId(taskId);
        apply(record, request);
        studyRecordMapper.insert(record);
        return requireOwnedRecord(taskId, record.getId(), userId);
    }

    public List<StudyRecord> listRecords(Long taskId, Long userId) {
        requireOwnedTask(taskId, userId);
        return studyRecordMapper.selectList(new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getTaskId, taskId)
                .eq(StudyRecord::getUserId, userId)
                .orderByDesc(StudyRecord::getStudiedAt)
                .orderByDesc(StudyRecord::getId));
    }

    public StudyRecord getRecord(Long taskId, Long recordId, Long userId) {
        requireOwnedTask(taskId, userId);
        return requireOwnedRecord(taskId, recordId, userId);
    }

    @Transactional
    public StudyRecord updateRecord(Long taskId, Long recordId, Long userId, StudyRecordRequest request) {
        requireOwnedTask(taskId, userId);
        StudyRecord record = requireOwnedRecord(taskId, recordId, userId);
        updateOrNotFound(studyRecordMapper.update(null, new LambdaUpdateWrapper<StudyRecord>()
                .set(StudyRecord::getStudiedAt, request.getStudiedAt())
                .set(StudyRecord::getDurationMinutes, request.getDurationMinutes())
                .set(StudyRecord::getContent, request.getContent())
                .eq(StudyRecord::getId, recordId)
                .eq(StudyRecord::getTaskId, taskId)
                .eq(StudyRecord::getUserId, userId)));
        return requireOwnedRecord(taskId, recordId, userId);
    }

    @Transactional
    public void deleteRecord(Long taskId, Long recordId, Long userId) {
        requireOwnedTask(taskId, userId);
        deleteOrNotFound(studyRecordMapper.delete(new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getId, recordId)
                .eq(StudyRecord::getTaskId, taskId)
                .eq(StudyRecord::getUserId, userId)));
    }

    public WeeklyReview getReview(Long planId, Long userId) {
        requireOwnedPlan(planId, userId);
        return requireOwnedReview(planId, userId);
    }

    @Transactional
    public WeeklyReview upsertReview(Long planId, Long userId, WeeklyReviewRequest request) {
        requireOwnedPlanForUpdate(planId, userId);
        WeeklyReview review = weeklyReviewMapper.selectOne(new LambdaQueryWrapper<WeeklyReview>()
                .eq(WeeklyReview::getPlanId, planId)
                .eq(WeeklyReview::getUserId, userId));
        if (review == null) {
            review = new WeeklyReview();
            review.setUserId(userId);
            review.setPlanId(planId);
            apply(review, request);
            try {
                weeklyReviewMapper.insert(review);
            } catch (DuplicateKeyException exception) {
                throw new DuplicateResourceException("同一学习计划只能有一份周复盘");
            }
        } else {
            updateReview(review.getId(), planId, userId, request);
        }
        return requireOwnedReview(planId, userId);
    }

    @Transactional
    public LearningNote createNote(Long planId, Long userId, LearningNoteRequest request) {
        requireOwnedPlan(planId, userId);
        validateTaskInPlanIfPresent(planId, request.getTaskId(), userId);
        LearningNote note = new LearningNote();
        note.setUserId(userId);
        note.setPlanId(planId);
        apply(note, request);
        learningNoteMapper.insert(note);
        return requireOwnedNote(planId, note.getId(), userId);
    }

    public List<LearningNote> listNotes(Long planId, Long userId) {
        requireOwnedPlan(planId, userId);
        return learningNoteMapper.selectList(new LambdaQueryWrapper<LearningNote>()
                .eq(LearningNote::getPlanId, planId)
                .eq(LearningNote::getUserId, userId)
                .orderByDesc(LearningNote::getCreatedAt)
                .orderByDesc(LearningNote::getId));
    }

    public LearningNote getNote(Long planId, Long noteId, Long userId) {
        requireOwnedPlan(planId, userId);
        return requireOwnedNote(planId, noteId, userId);
    }

    @Transactional
    public LearningNote updateNote(Long planId, Long noteId, Long userId, LearningNoteRequest request) {
        requireOwnedPlan(planId, userId);
        validateTaskInPlanIfPresent(planId, request.getTaskId(), userId);
        requireOwnedNote(planId, noteId, userId);
        updateOrNotFound(learningNoteMapper.update(null, new LambdaUpdateWrapper<LearningNote>()
                .set(LearningNote::getTaskId, request.getTaskId())
                .set(LearningNote::getTitle, request.getTitle())
                .set(LearningNote::getContent, request.getContent())
                .eq(LearningNote::getId, noteId)
                .eq(LearningNote::getPlanId, planId)
                .eq(LearningNote::getUserId, userId)));
        return requireOwnedNote(planId, noteId, userId);
    }

    @Transactional
    public void deleteNote(Long planId, Long noteId, Long userId) {
        requireOwnedPlan(planId, userId);
        requireOwnedNote(planId, noteId, userId);
        deleteOrNotFound(learningNoteMapper.delete(new LambdaQueryWrapper<LearningNote>()
                .eq(LearningNote::getId, noteId)
                .eq(LearningNote::getPlanId, planId)
                .eq(LearningNote::getUserId, userId)));
    }

    @Transactional
    public LearningMaterial createMaterial(Long planId, Long userId, LearningMaterialRequest request) {
        requireOwnedPlan(planId, userId);
        validateTaskInPlanIfPresent(planId, request.getTaskId(), userId);
        LearningMaterial material = new LearningMaterial();
        material.setUserId(userId);
        material.setPlanId(planId);
        apply(material, request);
        learningMaterialMapper.insert(material);
        return requireOwnedMaterial(planId, material.getId(), userId);
    }

    public List<LearningMaterial> listMaterials(Long planId, Long userId) {
        requireOwnedPlan(planId, userId);
        return learningMaterialMapper.selectList(new LambdaQueryWrapper<LearningMaterial>()
                .eq(LearningMaterial::getPlanId, planId)
                .eq(LearningMaterial::getUserId, userId)
                .orderByDesc(LearningMaterial::getCreatedAt)
                .orderByDesc(LearningMaterial::getId));
    }

    public LearningMaterial getMaterial(Long planId, Long materialId, Long userId) {
        requireOwnedPlan(planId, userId);
        return requireOwnedMaterial(planId, materialId, userId);
    }

    @Transactional
    public LearningMaterial updateMaterial(Long planId, Long materialId, Long userId,
                                            LearningMaterialRequest request) {
        requireOwnedPlan(planId, userId);
        validateTaskInPlanIfPresent(planId, request.getTaskId(), userId);
        requireOwnedMaterial(planId, materialId, userId);
        updateOrNotFound(learningMaterialMapper.update(null, new LambdaUpdateWrapper<LearningMaterial>()
                .set(LearningMaterial::getTaskId, request.getTaskId())
                .set(LearningMaterial::getTitle, request.getTitle())
                .set(LearningMaterial::getSourceUrl, request.getSourceUrl())
                .set(LearningMaterial::getDescription, request.getDescription())
                .eq(LearningMaterial::getId, materialId)
                .eq(LearningMaterial::getPlanId, planId)
                .eq(LearningMaterial::getUserId, userId)));
        return requireOwnedMaterial(planId, materialId, userId);
    }

    @Transactional
    public void deleteMaterial(Long planId, Long materialId, Long userId) {
        requireOwnedPlan(planId, userId);
        requireOwnedMaterial(planId, materialId, userId);
        deleteOrNotFound(learningMaterialMapper.delete(new LambdaQueryWrapper<LearningMaterial>()
                .eq(LearningMaterial::getId, materialId)
                .eq(LearningMaterial::getPlanId, planId)
                .eq(LearningMaterial::getUserId, userId)));
    }

    private LearningPlan requireOwnedPlan(Long planId, Long userId) {
        LearningPlan plan = learningPlanMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId));
        if (plan == null) {
            throw notFound();
        }
        return plan;
    }

    private LearningPlan requireOwnedPlanForUpdate(Long planId, Long userId) {
        LearningPlan plan = learningPlanMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId)
                .last("FOR UPDATE"));
        if (plan == null) {
            throw notFound();
        }
        return plan;
    }

    private LearningTask requireOwnedTask(Long taskId, Long userId) {
        LearningTask task = learningTaskMapper.selectOne(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getId, taskId)
                .eq(LearningTask::getUserId, userId));
        if (task == null) {
            throw notFound();
        }
        return task;
    }

    private LearningTask requireOwnedTaskInPlan(Long planId, Long taskId, Long userId) {
        LearningTask task = learningTaskMapper.selectOne(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getId, taskId)
                .eq(LearningTask::getPlanId, planId)
                .eq(LearningTask::getUserId, userId));
        if (task == null) {
            throw notFound();
        }
        return task;
    }

    private StudyRecord requireOwnedRecord(Long taskId, Long recordId, Long userId) {
        StudyRecord record = studyRecordMapper.selectOne(new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getId, recordId)
                .eq(StudyRecord::getTaskId, taskId)
                .eq(StudyRecord::getUserId, userId));
        if (record == null) {
            throw notFound();
        }
        return record;
    }

    private WeeklyReview requireOwnedReview(Long planId, Long userId) {
        WeeklyReview review = weeklyReviewMapper.selectOne(new LambdaQueryWrapper<WeeklyReview>()
                .eq(WeeklyReview::getPlanId, planId)
                .eq(WeeklyReview::getUserId, userId));
        if (review == null) {
            throw notFound();
        }
        return review;
    }

    private LearningNote requireOwnedNote(Long planId, Long noteId, Long userId) {
        LearningNote note = learningNoteMapper.selectOne(new LambdaQueryWrapper<LearningNote>()
                .eq(LearningNote::getId, noteId)
                .eq(LearningNote::getPlanId, planId)
                .eq(LearningNote::getUserId, userId));
        if (note == null) {
            throw notFound();
        }
        return note;
    }

    private LearningMaterial requireOwnedMaterial(Long planId, Long materialId, Long userId) {
        LearningMaterial material = learningMaterialMapper.selectOne(new LambdaQueryWrapper<LearningMaterial>()
                .eq(LearningMaterial::getId, materialId)
                .eq(LearningMaterial::getPlanId, planId)
                .eq(LearningMaterial::getUserId, userId));
        if (material == null) {
            throw notFound();
        }
        return material;
    }

    private void validateTaskInPlanIfPresent(Long planId, Long taskId, Long userId) {
        if (taskId != null) {
            requireOwnedTaskInPlan(planId, taskId, userId);
        }
    }

    private void updateReview(Long reviewId, Long planId, Long userId, WeeklyReviewRequest request) {
        updateOrNotFound(weeklyReviewMapper.update(null, new LambdaUpdateWrapper<WeeklyReview>()
                .set(WeeklyReview::getSummary, request.getSummary())
                .set(WeeklyReview::getAchievements, request.getAchievements())
                .set(WeeklyReview::getProblems, request.getProblems())
                .set(WeeklyReview::getNextSteps, request.getNextSteps())
                .set(WeeklyReview::getReviewedAt, request.getReviewedAt())
                .eq(WeeklyReview::getId, reviewId)
                .eq(WeeklyReview::getPlanId, planId)
                .eq(WeeklyReview::getUserId, userId)));
    }

    private void ensurePlanWeekStartAvailable(Long userId, LocalDate weekStart, Long ignoredPlanId) {
        LambdaQueryWrapper<LearningPlan> query = new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getWeekStart, weekStart);
        if (ignoredPlanId != null) {
            query.ne(LearningPlan::getId, ignoredPlanId);
        }
        if (learningPlanMapper.selectCount(query) > 0) {
            throw duplicatePlan();
        }
    }

    private void validatePlanDates(LocalDate weekStart, LocalDate weekEnd) {
        if (weekStart != null && weekEnd != null && weekEnd.isBefore(weekStart)) {
            throw new InvalidRequestException("计划结束日期不能早于开始日期");
        }
    }

    private void validatePlanBundle(
            LearningPlanRequest planRequest,
            List<LearningTaskRequest> taskRequests,
            int availableMinutes) {
        if (planRequest == null || planRequest.getWeekStart() == null || planRequest.getWeekEnd() == null) {
            throw new InvalidRequestException("计划日期不能为空");
        }
        validatePlanDates(planRequest.getWeekStart(), planRequest.getWeekEnd());
        if (planRequest.getMainGoal() == null || planRequest.getMainGoal().trim().isEmpty()
                || planRequest.getMainGoal().trim().length() > 500) {
            throw new InvalidRequestException("计划目标无效");
        }
        if (planRequest.getStatus() != LearningPlanStatus.PLANNED) {
            throw new InvalidRequestException("AI 候选计划的初始状态必须为 PLANNED");
        }
        if (availableMinutes <= 0 || availableMinutes > 10_080) {
            throw new InvalidRequestException("每周可用时间必须在1到10080分钟之间");
        }
        if (taskRequests == null || taskRequests.isEmpty() || taskRequests.size() > 6) {
            throw new InvalidRequestException("确认时需保留1到6个学习任务");
        }

        Set<String> titles = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        long totalMinutes = 0;
        for (LearningTaskRequest task : taskRequests) {
            if (task == null || task.getTitle() == null || task.getTitle().trim().isEmpty()
                    || task.getTitle().trim().length() > 200) {
                throw new InvalidRequestException("学习任务标题无效");
            }
            if (!titles.add(task.getTitle().trim().toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("学习任务标题不能重复");
            }
            if (task.getDescription() != null && task.getDescription().length() > 2_000) {
                throw new InvalidRequestException("AI 候选任务描述长度不能超过2000个字符");
            }
            if (task.getStatus() != LearningTaskStatus.TODO) {
                throw new InvalidRequestException("AI 候选任务的初始状态必须为 TODO");
            }
            if (task.getPlannedMinutes() == null || task.getPlannedMinutes() <= 0) {
                throw new InvalidRequestException("任务计划用时必须大于0");
            }
            if (task.getDueDate() == null) {
                throw new InvalidRequestException("任务截止日期不能为空");
            }
            LearningPlan dateBoundary = new LearningPlan();
            dateBoundary.setWeekStart(planRequest.getWeekStart());
            dateBoundary.setWeekEnd(planRequest.getWeekEnd());
            validateTaskDueDate(dateBoundary, task.getDueDate());
            if (task.getSortOrder() == null || task.getSortOrder() < 0) {
                throw new InvalidRequestException("任务排序值不能小于0");
            }
            if (!sortOrders.add(task.getSortOrder())) {
                throw new InvalidRequestException("任务排序值不能重复");
            }
            totalMinutes += task.getPlannedMinutes();
        }
        if (totalMinutes > availableMinutes) {
            throw new InvalidRequestException("任务总计划用时不能超过每周可用时间");
        }
    }

    private void validateTaskDueDate(LearningPlan plan, LocalDate dueDate) {
        if (dueDate != null && (dueDate.isBefore(plan.getWeekStart()) || dueDate.isAfter(plan.getWeekEnd()))) {
            throw new InvalidRequestException("任务截止日期必须在计划日期范围内");
        }
    }

    private void validateTaskDueDatesWithinPlan(Long planId, Long userId,
                                                LocalDate weekStart, LocalDate weekEnd) {
        if (weekStart == null || weekEnd == null) {
            return;
        }
        long outsideRangeCount = learningTaskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getPlanId, planId)
                .eq(LearningTask::getUserId, userId)
                .isNotNull(LearningTask::getDueDate)
                .and(query -> query.lt(LearningTask::getDueDate, weekStart)
                        .or()
                        .gt(LearningTask::getDueDate, weekEnd)));
        if (outsideRangeCount > 0) {
            throw new InvalidRequestException("计划日期范围不能排除已有任务的截止日期");
        }
    }

    private void apply(LearningPlan entity, LearningPlanRequest request) {
        entity.setWeekStart(request.getWeekStart());
        entity.setWeekEnd(request.getWeekEnd());
        entity.setMainGoal(request.getMainGoal());
        entity.setStatus(request.getStatus());
    }

    private void apply(LearningTask entity, LearningTaskRequest request) {
        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setStatus(request.getStatus());
        entity.setPlannedMinutes(request.getPlannedMinutes());
        entity.setDueDate(request.getDueDate());
        entity.setSortOrder(request.getSortOrder());
    }

    private void apply(StudyRecord entity, StudyRecordRequest request) {
        entity.setStudiedAt(request.getStudiedAt());
        entity.setDurationMinutes(request.getDurationMinutes());
        entity.setContent(request.getContent());
    }

    private void apply(WeeklyReview entity, WeeklyReviewRequest request) {
        entity.setSummary(request.getSummary());
        entity.setAchievements(request.getAchievements());
        entity.setProblems(request.getProblems());
        entity.setNextSteps(request.getNextSteps());
        entity.setReviewedAt(request.getReviewedAt());
    }

    private void apply(LearningNote entity, LearningNoteRequest request) {
        entity.setTaskId(request.getTaskId());
        entity.setTitle(request.getTitle());
        entity.setContent(request.getContent());
    }

    private void apply(LearningMaterial entity, LearningMaterialRequest request) {
        entity.setTaskId(request.getTaskId());
        entity.setTitle(request.getTitle());
        entity.setSourceUrl(request.getSourceUrl());
        entity.setDescription(request.getDescription());
    }

    private void updateOrNotFound(int affectedRows) {
        if (affectedRows == 0) {
            throw notFound();
        }
    }

    private void deleteOrNotFound(int affectedRows) {
        if (affectedRows == 0) {
            throw notFound();
        }
    }

    private DuplicateResourceException duplicatePlan() {
        return new DuplicateResourceException("同一用户同一周起始日期只能创建一个学习计划");
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("资源不存在");
    }

    public record CreatedPlanWithTasks(LearningPlan plan, List<LearningTask> tasks) { }
}
