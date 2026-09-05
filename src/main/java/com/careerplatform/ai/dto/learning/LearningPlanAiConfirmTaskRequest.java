package com.careerplatform.ai.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** User-edited task accepted at the deterministic plan creation boundary. */
public class LearningPlanAiConfirmTaskRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200, message = "任务标题长度不能超过200个字符")
    private String title;

    @Size(max = 2_000, message = "AI 候选任务描述长度不能超过2000个字符")
    private String description;

    @NotNull(message = "计划用时不能为空")
    @Positive(message = "计划用时必须大于0")
    private Integer plannedMinutes;

    @NotNull(message = "任务截止日期不能为空")
    private LocalDate dueDate;

    @NotNull(message = "排序值不能为空")
    @PositiveOrZero(message = "排序值不能小于0")
    private Integer sortOrder;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getPlannedMinutes() { return plannedMinutes; }
    public void setPlannedMinutes(Integer plannedMinutes) { this.plannedMinutes = plannedMinutes; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
