package com.careerplatform.learning.dto;

import java.time.LocalDateTime;

public record StudyRecordResponse(Long id, LocalDateTime studiedAt, Integer durationMinutes,
                                  String content, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
