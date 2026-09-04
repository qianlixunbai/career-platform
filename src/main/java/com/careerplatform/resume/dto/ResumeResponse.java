package com.careerplatform.resume.dto;

import java.time.LocalDateTime;

public record ResumeResponse(Long id, String name, String description,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
}
