package com.careerplatform.application.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class OfferCreateRequest {

    @Size(max = 150, message = "职位名称长度不能超过150个字符")
    private String positionTitle;

    @Size(max = 255, message = "薪酬信息长度不能超过255个字符")
    private String compensation;

    private LocalDateTime expiresAt;

    @Size(max = 16_000, message = "Offer备注长度不能超过16000个字符")
    private String notes;

    public String getPositionTitle() {
        return positionTitle;
    }

    public void setPositionTitle(String positionTitle) {
        this.positionTitle = positionTitle;
    }

    public String getCompensation() {
        return compensation;
    }

    public void setCompensation(String compensation) {
        this.compensation = compensation;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
