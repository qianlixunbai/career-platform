package com.careerplatform.application.dto;

import com.careerplatform.application.enums.InterviewResult;
import com.careerplatform.application.enums.InterviewType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class InterviewRequest {

    @NotNull(message = "面试轮次不能为空")
    @Min(value = 1, message = "面试轮次必须从1开始")
    private Integer roundNo;

    @NotNull(message = "面试类型不能为空")
    private InterviewType type;

    @NotBlank(message = "面试标题不能为空")
    @Size(max = 200, message = "面试标题长度不能超过200个字符")
    private String title;

    private LocalDateTime scheduledAt;

    private LocalDateTime occurredAt;

    private InterviewResult result;

    @Size(max = 16_000, message = "面试备注长度不能超过16000个字符")
    private String notes;

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public InterviewType getType() {
        return type;
    }

    public void setType(InterviewType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public InterviewResult getResult() {
        return result;
    }

    public void setResult(InterviewResult result) {
        this.result = result;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
