package com.careerplatform.application.dto;

import com.careerplatform.application.enums.ApplicationEndReason;
import com.careerplatform.application.enums.ApplicationStage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ApplicationTransitionRequest {

    @NotNull(message = "目标阶段不能为空")
    private ApplicationStage targetStage;

    private ApplicationEndReason endReason;

    @Size(max = 1_000, message = "备注长度不能超过1000个字符")
    private String note;

    public ApplicationStage getTargetStage() {
        return targetStage;
    }

    public void setTargetStage(ApplicationStage targetStage) {
        this.targetStage = targetStage;
    }

    public ApplicationEndReason getEndReason() {
        return endReason;
    }

    public void setEndReason(ApplicationEndReason endReason) {
        this.endReason = endReason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
