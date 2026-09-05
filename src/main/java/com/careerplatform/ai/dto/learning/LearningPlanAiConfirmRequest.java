package com.careerplatform.ai.dto.learning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** User-confirmed plan candidate. No model-controlled identifier is accepted. */
public class LearningPlanAiConfirmRequest {

    @NotNull(message = "计划开始日期不能为空")
    private LocalDate weekStart;

    @NotNull(message = "计划结束日期不能为空")
    private LocalDate weekEnd;

    @NotNull(message = "每周可用时间不能为空")
    @Min(value = 1, message = "每周可用时间必须大于0")
    @Max(value = 10_080, message = "每周可用时间不能超过10080分钟")
    private Integer availableMinutes;

    @NotBlank(message = "计划目标不能为空")
    @Size(max = 500, message = "计划目标长度不能超过500个字符")
    private String mainGoal;

    @NotNull(message = "学习任务不能为空")
    @Size(min = 1, max = 6, message = "确认时需保留1到6个学习任务")
    @Valid
    private List<LearningPlanAiConfirmTaskRequest> tasks;

    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    public LocalDate getWeekEnd() { return weekEnd; }
    public void setWeekEnd(LocalDate weekEnd) { this.weekEnd = weekEnd; }
    public Integer getAvailableMinutes() { return availableMinutes; }
    public void setAvailableMinutes(Integer availableMinutes) { this.availableMinutes = availableMinutes; }
    public String getMainGoal() { return mainGoal; }
    public void setMainGoal(String mainGoal) { this.mainGoal = mainGoal; }
    public List<LearningPlanAiConfirmTaskRequest> getTasks() { return tasks; }
    public void setTasks(List<LearningPlanAiConfirmTaskRequest> tasks) { this.tasks = tasks; }
}
