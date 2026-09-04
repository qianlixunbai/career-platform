package com.careerplatform.application.dto;

import com.careerplatform.application.enums.AssessmentResult;
import com.careerplatform.application.enums.AssessmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class AssessmentRequest {

    @NotNull(message = "测评类型不能为空")
    private AssessmentType type;

    @NotBlank(message = "测评标题不能为空")
    @Size(max = 200, message = "测评标题长度不能超过200个字符")
    private String title;

    private LocalDateTime scheduledAt;

    private LocalDateTime occurredAt;

    private AssessmentResult result;

    @Size(max = 16_000, message = "测评备注长度不能超过16000个字符")
    private String notes;

    public AssessmentType getType() {
        return type;
    }

    public void setType(AssessmentType type) {
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

    public AssessmentResult getResult() {
        return result;
    }

    public void setResult(AssessmentResult result) {
        this.result = result;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
