package com.careerplatform.learning.dto;

import com.careerplatform.learning.enums.LearningPlanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class LearningPlanRequest {

    @NotNull(message = "计划开始日期不能为空")
    private LocalDate weekStart;

    @NotNull(message = "计划结束日期不能为空")
    private LocalDate weekEnd;

    @NotBlank(message = "计划目标不能为空")
    @Size(max = 500, message = "计划目标长度不能超过500个字符")
    private String mainGoal;

    @NotNull(message = "计划状态不能为空")
    private LearningPlanStatus status;

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public LocalDate getWeekEnd() {
        return weekEnd;
    }

    public void setWeekEnd(LocalDate weekEnd) {
        this.weekEnd = weekEnd;
    }

    public String getMainGoal() {
        return mainGoal;
    }

    public void setMainGoal(String mainGoal) {
        this.mainGoal = mainGoal;
    }

    public LearningPlanStatus getStatus() {
        return status;
    }

    public void setStatus(LearningPlanStatus status) {
        this.status = status;
    }
}
