package com.careerplatform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerplatform.ai.dto.learning.LearningAiEvidence;
import com.careerplatform.ai.dto.learning.LearningAiEvidenceType;
import com.careerplatform.ai.dto.learning.LearningAiReviewMetrics;
import com.careerplatform.ai.dto.learning.LearningAiTaskMetric;
import com.careerplatform.ai.dto.learning.LearningPlanAiSuggestionRequest;
import com.careerplatform.career.entity.CareerGoal;
import com.careerplatform.career.entity.Job;
import com.careerplatform.career.entity.JobRequirement;
import com.careerplatform.career.service.CareerService;
import com.careerplatform.auth.UnauthorizedException;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.learning.entity.LearningNote;
import com.careerplatform.learning.entity.LearningPlan;
import com.careerplatform.learning.entity.LearningTask;
import com.careerplatform.learning.entity.StudyRecord;
import com.careerplatform.learning.entity.WeeklyReview;
import com.careerplatform.learning.enums.LearningTaskStatus;
import com.careerplatform.learning.mapper.LearningNoteMapper;
import com.careerplatform.learning.mapper.LearningPlanMapper;
import com.careerplatform.learning.mapper.LearningTaskMapper;
import com.careerplatform.learning.mapper.StudyRecordMapper;
import com.careerplatform.learning.mapper.WeeklyReviewMapper;
import com.careerplatform.profile.service.ProfileService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a bounded, owner-aware context before an AI request is made.
 *
 * <p>This class deliberately reads Learning tables with a {@code userId}
 * predicate on every query.  It also owns the evidence map, so provider text
 * cannot become a trusted source merely by naming a database id.</p>
 */
@Component
public class LearningAiContextBuilder {
    public static final int MAX_SKILLS = 30;
    public static final int MAX_JOBS = 5;
    public static final int MAX_REQUIREMENTS_PER_JOB = 10;
    public static final int MAX_REQUIREMENTS_TOTAL = 30;
    public static final int MAX_RECENT_PLANS = 2;
    public static final int MAX_RECENT_TASKS = 20;
    public static final int MAX_STUDY_RECORDS = 30;
    public static final int MAX_WEEKLY_REVIEWS = 2;
    public static final int MAX_LEARNING_NOTES = 12;
    public static final int MAX_SEGMENT_LENGTH = 300;
    public static final int MAX_CONTEXT_TEXT_LENGTH = 12_000;

    private static final String TRUNCATION_WARNING = "部分历史数据因上下文上限未参与本次分析。";

    private final CareerService careerService;
    private final ProfileService profileService;
    private final LearningPlanMapper learningPlanMapper;
    private final LearningTaskMapper learningTaskMapper;
    private final StudyRecordMapper studyRecordMapper;
    private final WeeklyReviewMapper weeklyReviewMapper;
    private final LearningNoteMapper learningNoteMapper;

    public LearningAiContextBuilder(CareerService careerService,
                                    ProfileService profileService,
                                    LearningPlanMapper learningPlanMapper,
                                    LearningTaskMapper learningTaskMapper,
                                    StudyRecordMapper studyRecordMapper,
                                    WeeklyReviewMapper weeklyReviewMapper,
                                    LearningNoteMapper learningNoteMapper) {
        this.careerService = Objects.requireNonNull(careerService, "careerService must not be null");
        this.profileService = Objects.requireNonNull(profileService, "profileService must not be null");
        this.learningPlanMapper = Objects.requireNonNull(learningPlanMapper, "learningPlanMapper must not be null");
        this.learningTaskMapper = Objects.requireNonNull(learningTaskMapper, "learningTaskMapper must not be null");
        this.studyRecordMapper = Objects.requireNonNull(studyRecordMapper, "studyRecordMapper must not be null");
        this.weeklyReviewMapper = Objects.requireNonNull(weeklyReviewMapper, "weeklyReviewMapper must not be null");
        this.learningNoteMapper = Objects.requireNonNull(learningNoteMapper, "learningNoteMapper must not be null");
    }

    /** Build the complete but bounded context for a plan suggestion. */
    @Transactional(readOnly = true)
    public PlanContext buildPlanContext(Long userId, LearningPlanAiSuggestionRequest request) {
        requireUserId(userId);
        validatePlanRequest(request);

        WarningCollector warnings = new WarningCollector();
        ContextAccumulator evidence = new ContextAccumulator(warnings);
        evidence.add(
                "USER_FOCUS:TIME_BUDGET",
                LearningAiEvidenceType.USER_FOCUS,
                "用户明确提供的计划约束",
                request.getWeekStart() + " 至 " + request.getWeekEnd()
                        + "，可用时间=" + request.getAvailableMinutes() + "分钟");
        if (!isBlank(request.getFocusNote())) {
            evidence.add("USER_FOCUS:CURRENT", LearningAiEvidenceType.USER_FOCUS,
                    "本周关注", request.getFocusNote());
        }

        CareerGoal careerGoal = null;
        if (request.getCareerGoalId() != null) {
            // CareerService performs the owner check before returning the goal.
            careerGoal = careerService.getGoal(request.getCareerGoalId(), userId);
            if (careerGoal == null) {
                throw new ResourceNotFoundException("资源不存在");
            }
            evidence.add(
                    key(LearningAiEvidenceType.CAREER_GOAL, careerGoal.getId(), "goal"),
                    LearningAiEvidenceType.CAREER_GOAL,
                    "职业目标 #" + token(careerGoal.getId(), "goal"),
                    joinNonBlank(", ",
                            careerGoal.getTargetPosition(),
                            careerGoal.getTargetCity(),
                            careerGoal.getTargetIndustry(),
                            careerGoal.getTargetCompanyPreference(),
                            careerGoal.getNotes()));
        }

        // Copy defensively without List.copyOf so a null element can be
        // reported as a normal request validation error rather than an NPE.
        List<Long> selectedJobIds = request.getSelectedJobIds() == null
                ? List.of() : new ArrayList<>(request.getSelectedJobIds());
        if (selectedJobIds.size() > MAX_JOBS) {
            throw new InvalidRequestException("关注岗位最多选择5个");
        }
        Set<Long> distinctJobIds = new HashSet<>();
        List<JobContext> selectedJobs = new ArrayList<>();
        int totalRequirements = 0;
        for (Long selectedJobId : selectedJobIds) {
            if (selectedJobId == null || !distinctJobIds.add(selectedJobId)) {
                throw new InvalidRequestException("关注岗位不能重复或为空");
            }
            // getJob is owner-aware; never load a selected job by id alone.
            Job job = careerService.getJob(selectedJobId, userId);
            if (job == null) {
                throw new ResourceNotFoundException("资源不存在");
            }
            Long ownedJobId = job.getId() == null ? selectedJobId : job.getId();
            List<JobRequirement> allRequirements = safeList(careerService.listRequirements(ownedJobId, userId));
            if (allRequirements.size() > MAX_REQUIREMENTS_PER_JOB) {
                warnings.add(TRUNCATION_WARNING);
            }
            List<JobRequirement> requirements = new ArrayList<>();
            for (JobRequirement requirement : allRequirements) {
                if (requirement == null) {
                    continue;
                }
                if (requirements.size() >= MAX_REQUIREMENTS_PER_JOB
                        || totalRequirements >= MAX_REQUIREMENTS_TOTAL) {
                    warnings.add(TRUNCATION_WARNING);
                    break;
                }
                requirements.add(requirement);
                totalRequirements++;
                evidence.add(
                        key(LearningAiEvidenceType.JOB_REQUIREMENT, requirement.getId(), "job-" + token(ownedJobId, "job")),
                        LearningAiEvidenceType.JOB_REQUIREMENT,
                        joinNonBlank(" · ", job.getTitle(), "结构化岗位要求"),
                        joinNonBlank(" ", requirement.getRequirementType() == null ? null : requirement.getRequirementType().name(),
                                requirement.getRequirementText()));
            }
            selectedJobs.add(new JobContext(job, List.copyOf(requirements)));
        }
        // Explicitly selected goal/jobs/focus take budget priority over the
        // broader skill inventory and historical context.
        List<ProfileService.UserSkillDetail> userSkills = boundedSkills(userId, warnings, evidence);
        List<LearningPlan> allRecentPlans = safeList(learningPlanMapper.selectList(
                new LambdaQueryWrapper<LearningPlan>()
                        .eq(LearningPlan::getUserId, userId)
                        .orderByDesc(LearningPlan::getWeekStart)
                        .orderByDesc(LearningPlan::getId)
                        .last("LIMIT " + (MAX_RECENT_PLANS + 1))));
        if (allRecentPlans.size() > MAX_RECENT_PLANS) {
            warnings.add(TRUNCATION_WARNING);
        }
        List<LearningPlan> recentPlans = bounded(allRecentPlans, MAX_RECENT_PLANS);
        for (LearningPlan recentPlan : recentPlans) {
            evidence.add(
                    key(LearningAiEvidenceType.LEARNING_PLAN, recentPlan.getId(), "recent-plan"),
                    LearningAiEvidenceType.LEARNING_PLAN,
                    "近期学习计划",
                    joinNonBlank(" | ",
                            recentPlan.getMainGoal(),
                            recentPlan.getStatus() == null ? null : "状态=" + recentPlan.getStatus().name(),
                            recentPlan.getWeekStart() == null ? null : "开始=" + recentPlan.getWeekStart(),
                            recentPlan.getWeekEnd() == null ? null : "结束=" + recentPlan.getWeekEnd()));
        }

        List<LearningTask> recentTasks = new ArrayList<>();
        for (LearningPlan plan : recentPlans) {
            List<LearningTask> planTasks = safeList(learningTaskMapper.selectList(
                    new LambdaQueryWrapper<LearningTask>()
                            .eq(LearningTask::getPlanId, plan.getId())
                            .eq(LearningTask::getUserId, userId)
                            .orderByAsc(LearningTask::getSortOrder)
                            .orderByAsc(LearningTask::getId)
                            .last("LIMIT " + (MAX_RECENT_TASKS + 1))));
            for (LearningTask task : planTasks) {
                if (task == null) {
                    continue;
                }
                if (recentTasks.size() >= MAX_RECENT_TASKS) {
                    warnings.add(TRUNCATION_WARNING);
                    break;
                }
                recentTasks.add(task);
                evidence.add(
                        key(LearningAiEvidenceType.LEARNING_TASK, task.getId(), "recent-task"),
                        LearningAiEvidenceType.LEARNING_TASK,
                        "历史学习任务 #" + token(task.getId(), "task"),
                        taskExcerpt(task));
            }
            if (recentTasks.size() >= MAX_RECENT_TASKS) {
                break;
            }
        }

        List<Long> recentTaskIds = recentTasks.stream().map(LearningTask::getId)
                .filter(Objects::nonNull).toList();
        List<StudyRecord> allStudyRecords = recentTaskIds.isEmpty() ? List.of() : safeList(studyRecordMapper.selectList(
                new LambdaQueryWrapper<StudyRecord>()
                        .eq(StudyRecord::getUserId, userId)
                        .in(StudyRecord::getTaskId, recentTaskIds)
                        .orderByDesc(StudyRecord::getStudiedAt)
                        .orderByDesc(StudyRecord::getId)
                        .last("LIMIT " + (MAX_STUDY_RECORDS + 1))));
        if (allStudyRecords.size() > MAX_STUDY_RECORDS) {
            warnings.add(TRUNCATION_WARNING);
        }
        List<StudyRecord> studyRecords = bounded(allStudyRecords, MAX_STUDY_RECORDS);
        for (StudyRecord record : studyRecords) {
            evidence.add(
                    key(LearningAiEvidenceType.STUDY_RECORD, record.getId(), "study-record"),
                    LearningAiEvidenceType.STUDY_RECORD,
                    "学习记录 #" + token(record.getId(), "record"),
                    studyRecordExcerpt(record));
        }

        List<Long> recentPlanIds = recentPlans.stream().map(LearningPlan::getId)
                .filter(Objects::nonNull).toList();
        List<WeeklyReview> allWeeklyReviews = recentPlanIds.isEmpty() ? List.of() : safeList(weeklyReviewMapper.selectList(
                new LambdaQueryWrapper<WeeklyReview>()
                        .eq(WeeklyReview::getUserId, userId)
                        .in(WeeklyReview::getPlanId, recentPlanIds)
                        .orderByDesc(WeeklyReview::getReviewedAt)
                        .orderByDesc(WeeklyReview::getId)
                        .last("LIMIT " + (MAX_WEEKLY_REVIEWS + 1))));
        if (allWeeklyReviews.size() > MAX_WEEKLY_REVIEWS) {
            warnings.add(TRUNCATION_WARNING);
        }
        List<WeeklyReview> weeklyReviews = bounded(allWeeklyReviews, MAX_WEEKLY_REVIEWS);
        for (WeeklyReview review : weeklyReviews) {
            evidence.add(
                    key(LearningAiEvidenceType.WEEKLY_REVIEW, review.getId(), "review"),
                    LearningAiEvidenceType.WEEKLY_REVIEW,
                    "历史周复盘 #" + token(review.getId(), "review"),
                    weeklyReviewExcerpt(review));
        }

        List<LearningNote> allLearningNotes = recentPlanIds.isEmpty() ? List.of() : safeList(learningNoteMapper.selectList(
                new LambdaQueryWrapper<LearningNote>()
                        .eq(LearningNote::getUserId, userId)
                        .in(LearningNote::getPlanId, recentPlanIds)
                        .orderByDesc(LearningNote::getUpdatedAt)
                        .orderByDesc(LearningNote::getId)
                        .last("LIMIT " + (MAX_LEARNING_NOTES + 1))));
        if (allLearningNotes.size() > MAX_LEARNING_NOTES) {
            warnings.add(TRUNCATION_WARNING);
        }
        List<LearningNote> learningNotes = bounded(allLearningNotes, MAX_LEARNING_NOTES);
        for (LearningNote note : learningNotes) {
            evidence.add(
                    key(LearningAiEvidenceType.LEARNING_NOTE, note.getId(), "note"),
                    LearningAiEvidenceType.LEARNING_NOTE,
                    "学习笔记 #" + token(note.getId(), "note"),
                    joinNonBlank(" ", note.getTitle(), note.getContent()));
        }

        return new PlanContext(
                request,
                careerGoal,
                userSkills,
                List.copyOf(selectedJobs),
                List.copyOf(recentPlans),
                List.copyOf(recentTasks),
                List.copyOf(studyRecords),
                List.copyOf(weeklyReviews),
                List.copyOf(learningNotes),
                evidence.values(),
                warnings.values(),
                evidence.textLength());
    }

    /** Build an owner-checked context and deterministic metrics for review advice. */
    @Transactional(readOnly = true)
    public ReviewContext buildReviewContext(Long userId, Long planId) {
        requireUserId(userId);
        if (planId == null) {
            throw new InvalidRequestException("学习计划不能为空");
        }
        LearningPlan plan = learningPlanMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId));
        if (plan == null) {
            throw new ResourceNotFoundException("资源不存在");
        }

        WarningCollector warnings = new WarningCollector();
        ContextAccumulator evidence = new ContextAccumulator(warnings);
        evidence.add(
                key(LearningAiEvidenceType.LEARNING_PLAN, plan.getId(), "current-plan"),
                LearningAiEvidenceType.LEARNING_PLAN,
                "当前学习计划",
                joinNonBlank(" | ",
                        plan.getMainGoal(),
                        plan.getStatus() == null ? null : "状态=" + plan.getStatus().name(),
                        plan.getWeekStart() == null ? null : "开始=" + plan.getWeekStart(),
                        plan.getWeekEnd() == null ? null : "结束=" + plan.getWeekEnd()));
        WeeklyReview existingReview = weeklyReviewMapper.selectOne(new LambdaQueryWrapper<WeeklyReview>()
                .eq(WeeklyReview::getPlanId, planId)
                .eq(WeeklyReview::getUserId, userId));
        if (existingReview != null) {
            evidence.add(
                    key(LearningAiEvidenceType.WEEKLY_REVIEW, existingReview.getId(), "existing-review"),
                    LearningAiEvidenceType.WEEKLY_REVIEW,
                    "已有周复盘 #" + token(existingReview.getId(), "review"),
                    weeklyReviewExcerpt(existingReview));
        }
        List<LearningTask> tasks = safeList(learningTaskMapper.selectList(
                new LambdaQueryWrapper<LearningTask>()
                        .eq(LearningTask::getPlanId, planId)
                        .eq(LearningTask::getUserId, userId)
                        .orderByAsc(LearningTask::getSortOrder)
                        .orderByAsc(LearningTask::getId)));
        if (tasks.size() > MAX_RECENT_TASKS) {
            warnings.add(TRUNCATION_WARNING);
        }
        List<LearningTask> evidenceTasks = tasks.size() > MAX_RECENT_TASKS
                ? tasks.subList(0, MAX_RECENT_TASKS) : tasks;
        for (LearningTask task : evidenceTasks) {
            evidence.add(
                    key(LearningAiEvidenceType.LEARNING_TASK, task.getId(), "review-task"),
                    LearningAiEvidenceType.LEARNING_TASK,
                    "本周学习任务 #" + token(task.getId(), "task"),
                    taskExcerpt(task));
        }

        List<Long> taskIds = tasks.stream().map(LearningTask::getId).filter(Objects::nonNull).toList();
        // Fetch all records for deterministic metrics first.  Only the bounded
        // prefix is sent to the model/context evidence map.
        List<StudyRecord> allStudyRecords = taskIds.isEmpty() ? List.of() : safeList(studyRecordMapper.selectList(
                new LambdaQueryWrapper<StudyRecord>()
                        .eq(StudyRecord::getUserId, userId)
                        .in(StudyRecord::getTaskId, taskIds)
                        .orderByDesc(StudyRecord::getStudiedAt)
                        .orderByDesc(StudyRecord::getId)));
        if (allStudyRecords.size() > MAX_STUDY_RECORDS) {
            warnings.add(TRUNCATION_WARNING);
        }
        List<StudyRecord> studyRecords = bounded(allStudyRecords, MAX_STUDY_RECORDS);
        for (StudyRecord record : studyRecords) {
            evidence.add(
                    key(LearningAiEvidenceType.STUDY_RECORD, record.getId(), "review-record"),
                    LearningAiEvidenceType.STUDY_RECORD,
                    "本周学习记录 #" + token(record.getId(), "record"),
                    studyRecordExcerpt(record));
        }

        List<LearningNote> allNotes = safeList(learningNoteMapper.selectList(
                new LambdaQueryWrapper<LearningNote>()
                        .eq(LearningNote::getPlanId, planId)
                        .eq(LearningNote::getUserId, userId)
                        .orderByDesc(LearningNote::getUpdatedAt)
                        .orderByDesc(LearningNote::getId)
                        .last("LIMIT " + (MAX_LEARNING_NOTES + 1))));
        if (allNotes.size() > MAX_LEARNING_NOTES) {
            warnings.add(TRUNCATION_WARNING);
        }
        List<LearningNote> notes = bounded(allNotes, MAX_LEARNING_NOTES);
        for (LearningNote note : notes) {
            evidence.add(
                    key(LearningAiEvidenceType.LEARNING_NOTE, note.getId(), "review-note"),
                    LearningAiEvidenceType.LEARNING_NOTE,
                    "本周学习笔记 #" + token(note.getId(), "note"),
                    joinNonBlank(" ", note.getTitle(), note.getContent()));
        }

        return new ReviewContext(
                plan,
                List.copyOf(tasks),
                List.copyOf(studyRecords),
                List.copyOf(notes),
                existingReview,
                calculateMetrics(tasks, allStudyRecords),
                evidence.values(),
                warnings.values(),
                evidence.textLength());
    }

    /** Deterministically computes review metrics from the given persisted facts. */
    public LearningAiReviewMetrics calculateMetrics(List<LearningTask> tasks, List<StudyRecord> records) {
        List<LearningTask> safeTasks = safeList(tasks);
        List<StudyRecord> safeRecords = safeList(records);
        EnumMap<LearningTaskStatus, Integer> statusCounts = new EnumMap<>(LearningTaskStatus.class);
        for (LearningTaskStatus status : LearningTaskStatus.values()) {
            statusCounts.put(status, 0);
        }
        Map<Long, Integer> actualByTask = new LinkedHashMap<>();
        Map<Long, Integer> recordCountByTask = new LinkedHashMap<>();
        int plannedMinutes = 0;
        for (LearningTask task : safeTasks) {
            if (task == null) {
                continue;
            }
            if (task.getStatus() != null) {
                statusCounts.compute(task.getStatus(), (key, value) -> value == null ? 1 : value + 1);
            }
            plannedMinutes = safeAdd(plannedMinutes, nonNegative(task.getPlannedMinutes()));
            if (task.getId() != null) {
                actualByTask.put(task.getId(), 0);
                recordCountByTask.put(task.getId(), 0);
            }
        }
        int actualMinutes = 0;
        for (StudyRecord record : safeRecords) {
            if (record == null) {
                continue;
            }
            int duration = nonNegative(record.getDurationMinutes());
            actualMinutes = safeAdd(actualMinutes, duration);
            if (record.getTaskId() != null && actualByTask.containsKey(record.getTaskId())) {
                actualByTask.compute(record.getTaskId(), (key, value) -> safeAdd(value == null ? 0 : value, duration));
                recordCountByTask.compute(record.getTaskId(), (key, value) -> (value == null ? 0 : value) + 1);
            }
        }
        int taskCount = safeTasks.stream().filter(Objects::nonNull).toList().size();
        int doneCount = statusCounts.getOrDefault(LearningTaskStatus.DONE, 0);
        BigDecimal completionRate = taskCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(doneCount * 100.0d / taskCount).setScale(2, RoundingMode.HALF_UP);
        List<LearningAiTaskMetric> taskMetrics = new ArrayList<>();
        for (LearningTask task : safeTasks) {
            if (task == null) {
                continue;
            }
            taskMetrics.add(new LearningAiTaskMetric(
                    task.getId(),
                    clip(task.getTitle(), new WarningCollector()),
                    task.getStatus(),
                    nonNegative(task.getPlannedMinutes()),
                    task.getId() == null ? 0 : actualByTask.getOrDefault(task.getId(), 0),
                    task.getId() == null ? 0 : recordCountByTask.getOrDefault(task.getId(), 0)));
        }
        return new LearningAiReviewMetrics(
                taskCount,
                doneCount,
                statusCounts.getOrDefault(LearningTaskStatus.TODO, 0),
                statusCounts.getOrDefault(LearningTaskStatus.IN_PROGRESS, 0),
                statusCounts.getOrDefault(LearningTaskStatus.SKIPPED, 0),
                completionRate,
                plannedMinutes,
                actualMinutes,
                safeRecords.stream().filter(Objects::nonNull).toList().size(),
                Collections.unmodifiableMap(new EnumMap<>(statusCounts)),
                List.copyOf(taskMetrics));
    }

    private List<ProfileService.UserSkillDetail> boundedSkills(Long userId,
                                                                 WarningCollector warnings,
                                                                 ContextAccumulator evidence) {
        List<ProfileService.UserSkillDetail> allSkills = safeList(profileService.listUserSkills(userId));
        if (allSkills.size() > MAX_SKILLS) {
            warnings.add(TRUNCATION_WARNING);
        }
        List<ProfileService.UserSkillDetail> skills = bounded(allSkills, MAX_SKILLS);
        for (ProfileService.UserSkillDetail detail : skills) {
            if (detail == null || detail.userSkill() == null) {
                continue;
            }
            String skillName = detail.skill() == null ? null : detail.skill().getName();
            evidence.add(
                    key(LearningAiEvidenceType.USER_SKILL, detail.userSkill().getId(), "skill"),
                    LearningAiEvidenceType.USER_SKILL,
                    "用户技能 #" + token(detail.userSkill().getId(), "skill"),
                    joinNonBlank(" ", skillName, detail.userSkill().getProficiency() == null
                            ? null : "熟练度=" + detail.userSkill().getProficiency().name()));
        }
        return List.copyOf(skills);
    }

    private void validatePlanRequest(LearningPlanAiSuggestionRequest request) {
        if (request == null || request.getWeekStart() == null || request.getWeekEnd() == null
                || request.getAvailableMinutes() == null) {
            throw new InvalidRequestException("计划周期和每周可用时间不能为空");
        }
        if (request.getWeekEnd().isBefore(request.getWeekStart())) {
            throw new InvalidRequestException("计划结束日期不能早于开始日期");
        }
        if (request.getAvailableMinutes() <= 0 || request.getAvailableMinutes() > 10_080) {
            throw new InvalidRequestException("每周可用时间必须在1到10080分钟之间");
        }
        if (request.getSelectedJobIds() != null && request.getSelectedJobIds().size() > MAX_JOBS) {
            throw new InvalidRequestException("关注岗位最多选择5个");
        }
        if (request.getFocusNote() != null && request.getFocusNote().length() > 1_000) {
            throw new InvalidRequestException("本周关注内容长度不能超过1000个字符");
        }
    }

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
    }

    private static String taskExcerpt(LearningTask task) {
        if (task == null) {
            return "";
        }
        return joinNonBlank(" | ",
                task.getTitle(),
                task.getDescription(),
                task.getStatus() == null ? null : "状态=" + task.getStatus().name(),
                task.getPlannedMinutes() == null ? null : "计划=" + task.getPlannedMinutes() + "分钟",
                task.getDueDate() == null ? null : "截止=" + task.getDueDate());
    }

    private static String studyRecordExcerpt(StudyRecord record) {
        if (record == null) {
            return "";
        }
        return joinNonBlank(" | ",
                record.getStudiedAt() == null ? null : record.getStudiedAt().toString(),
                record.getDurationMinutes() == null ? null : "时长=" + record.getDurationMinutes() + "分钟",
                record.getContent());
    }

    private static String weeklyReviewExcerpt(WeeklyReview review) {
        if (review == null) {
            return "";
        }
        return joinNonBlank(" | ", review.getSummary(), review.getAchievements(), review.getProblems(), review.getNextSteps());
    }

    private static String key(LearningAiEvidenceType type, Long id, String fallback) {
        return type.name() + ":" + token(id, fallback);
    }

    private static String token(Long id, String fallback) {
        return id == null ? fallback : String.valueOf(id);
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> nonBlank = new ArrayList<>();
        for (String value : values) {
            if (!isBlank(value)) {
                nonBlank.add(value.trim());
            }
        }
        return String.join(separator, nonBlank);
    }

    private static String clip(String value, WarningCollector warnings) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= MAX_SEGMENT_LENGTH) {
            return normalized;
        }
        warnings.add(TRUNCATION_WARNING);
        return normalized.substring(0, MAX_SEGMENT_LENGTH);
    }

    private static int nonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private static int safeAdd(int left, int right) {
        long sum = (long) left + right;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> List<T> bounded(List<T> values, int maximum) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<T> nonNull = values.stream().filter(Objects::nonNull).toList();
        return nonNull.size() <= maximum ? List.copyOf(nonNull) : List.copyOf(nonNull.subList(0, maximum));
    }

    /** Selected job and the bounded structured requirements attached to it. */
    public record JobContext(Job job, List<JobRequirement> requirements) {
        public JobContext {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
        }
    }

    /** Immutable owner-scoped plan context and trusted evidence map. */
    public record PlanContext(
            LearningPlanAiSuggestionRequest request,
            CareerGoal careerGoal,
            List<ProfileService.UserSkillDetail> userSkills,
            List<JobContext> selectedJobs,
            List<LearningPlan> recentPlans,
            List<LearningTask> recentTasks,
            List<StudyRecord> studyRecords,
            List<WeeklyReview> weeklyReviews,
            List<LearningNote> learningNotes,
            Map<String, LearningAiEvidence> evidenceByKey,
            List<String> warnings,
            int contextTextLength) {
        public PlanContext {
            userSkills = userSkills == null ? List.of() : List.copyOf(userSkills);
            selectedJobs = selectedJobs == null ? List.of() : List.copyOf(selectedJobs);
            recentPlans = recentPlans == null ? List.of() : List.copyOf(recentPlans);
            recentTasks = recentTasks == null ? List.of() : List.copyOf(recentTasks);
            studyRecords = studyRecords == null ? List.of() : List.copyOf(studyRecords);
            weeklyReviews = weeklyReviews == null ? List.of() : List.copyOf(weeklyReviews);
            learningNotes = learningNotes == null ? List.of() : List.copyOf(learningNotes);
            evidenceByKey = evidenceByKey == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(evidenceByKey));
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    /** Immutable owner-scoped review context plus deterministic metrics. */
    public record ReviewContext(
            LearningPlan plan,
            List<LearningTask> tasks,
            List<StudyRecord> studyRecords,
            List<LearningNote> learningNotes,
            WeeklyReview existingReview,
            LearningAiReviewMetrics metrics,
            Map<String, LearningAiEvidence> evidenceByKey,
            List<String> warnings,
            int contextTextLength) {
        public ReviewContext {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            studyRecords = studyRecords == null ? List.of() : List.copyOf(studyRecords);
            learningNotes = learningNotes == null ? List.of() : List.copyOf(learningNotes);
            evidenceByKey = evidenceByKey == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(evidenceByKey));
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    private static final class WarningCollector {
        private final LinkedHashSet<String> values = new LinkedHashSet<>();

        void add(String value) {
            if (!isBlank(value)) {
                values.add(value);
            }
        }

        List<String> values() {
            return List.copyOf(values);
        }
    }

    private static final class ContextAccumulator {
        private final WarningCollector warnings;
        private final LinkedHashMap<String, LearningAiEvidence> values = new LinkedHashMap<>();
        private int textLength;

        private ContextAccumulator(WarningCollector warnings) {
            this.warnings = warnings;
        }

        void add(String sourceKey, LearningAiEvidenceType type, String label, String rawExcerpt) {
            if (sourceKey == null || type == null) {
                return;
            }
            String uniqueKey = sourceKey;
            int duplicateSuffix = 1;
            while (values.containsKey(uniqueKey)) {
                uniqueKey = sourceKey + "#" + duplicateSuffix++;
            }
            WarningCollector segmentWarnings = new WarningCollector();
            String excerpt = clip(rawExcerpt, segmentWarnings);
            segmentWarnings.values().forEach(warnings::add);
            String safeLabel = label == null ? "" : label;
            int cost = uniqueKey.length() + safeLabel.length() + excerpt.length() + 8;
            if (textLength + cost > MAX_CONTEXT_TEXT_LENGTH) {
                warnings.add(TRUNCATION_WARNING);
                return;
            }
            values.put(uniqueKey, new LearningAiEvidence(uniqueKey, type,
                    clip(safeLabel, warnings), excerpt));
            textLength += cost;
        }

        Map<String, LearningAiEvidence> values() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        int textLength() {
            return textLength;
        }
    }
}
