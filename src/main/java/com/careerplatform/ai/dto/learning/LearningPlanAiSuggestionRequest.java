package com.careerplatform.ai.dto.learning;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDate;
import java.util.List;

/** User-owned constraints for an ephemeral AI weekly-plan candidate. */
public class LearningPlanAiSuggestionRequest {

    @NotNull(message = "计划开始日期不能为空")
    private LocalDate weekStart;

    @NotNull(message = "计划结束日期不能为空")
    private LocalDate weekEnd;

    @NotNull(message = "每周可用时间不能为空")
    @Min(value = 1, message = "每周可用时间必须大于0")
    @Max(value = 10_080, message = "每周可用时间不能超过10080分钟")
    private Integer availableMinutes;

    private Long careerGoalId;

    @JsonAlias("jobIds")
    @Size(max = 5, message = "关注岗位最多选择5个")
    private List<Long> selectedJobIds;

    @Size(max = 1_000, message = "本周关注内容长度不能超过1000个字符")
    private String focusNote;

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

    public Integer getAvailableMinutes() {
        return availableMinutes;
    }

    public void setAvailableMinutes(Integer availableMinutes) {
        this.availableMinutes = availableMinutes;
    }

    public Long getCareerGoalId() {
        return careerGoalId;
    }

    public void setCareerGoalId(Long careerGoalId) {
        this.careerGoalId = careerGoalId;
    }

    public List<Long> getSelectedJobIds() {
        return selectedJobIds;
    }

    public void setSelectedJobIds(List<Long> selectedJobIds) {
        this.selectedJobIds = selectedJobIds;
    }

    /** Frontend-friendly alias; JSON deserialization also accepts {@code jobIds}. */
    public List<Long> getJobIds() {
        return selectedJobIds;
    }

    public void setJobIds(List<Long> jobIds) {
        this.selectedJobIds = jobIds;
    }

    public String getFocusNote() {
        return focusNote;
    }

    public void setFocusNote(String focusNote) {
        this.focusNote = focusNote;
    }
}
