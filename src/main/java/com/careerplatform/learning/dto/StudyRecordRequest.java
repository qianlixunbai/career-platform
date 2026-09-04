package com.careerplatform.learning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class StudyRecordRequest {

    @NotNull(message = "学习时间不能为空")
    private LocalDateTime studiedAt;

    @NotNull(message = "学习时长不能为空")
    @Positive(message = "学习时长必须大于0")
    private Integer durationMinutes;

    @Size(max = 16000, message = "学习内容长度不能超过16000个字符")
    private String content;

    public LocalDateTime getStudiedAt() {
        return studiedAt;
    }

    public void setStudiedAt(LocalDateTime studiedAt) {
        this.studiedAt = studiedAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
