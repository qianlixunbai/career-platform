package com.careerplatform.resume.dto;

import java.time.LocalDateTime;

public record ResumeFileMetadataResponse(Long versionId,
                                         String originalFilename,
                                         String contentType,
                                         Long fileSize,
                                         LocalDateTime createdAt,
                                         LocalDateTime updatedAt) {
}
