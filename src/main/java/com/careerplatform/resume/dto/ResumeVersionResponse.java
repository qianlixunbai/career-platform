package com.careerplatform.resume.dto;

import com.careerplatform.resume.enums.ResumeVersionStatus;

import java.time.LocalDateTime;

public record ResumeVersionResponse(Long id, Long resumeId, Integer versionNo, String label,
                                   ResumeVersionStatus status, LocalDateTime finalizedAt,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
}
