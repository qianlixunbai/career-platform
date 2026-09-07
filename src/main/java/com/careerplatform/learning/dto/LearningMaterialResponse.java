package com.careerplatform.learning.dto;

import java.time.LocalDateTime;

public record LearningMaterialResponse(Long id, Long planId, Long taskId, String title,
                                       String sourceUrl, String description, LocalDateTime createdAt,
                                       LocalDateTime updatedAt, String fileName, String contentType,
                                       Long fileSize, String indexStatus, Integer chunkCount,
                                       String embeddingIdentity) {

    /** Keeps the pre-file-upload constructor source compatible for existing callers. */
    public LearningMaterialResponse(Long id, Long planId, Long taskId, String title,
                                    String sourceUrl, String description, LocalDateTime createdAt,
                                    LocalDateTime updatedAt) {
        this(id, planId, taskId, title, sourceUrl, description, createdAt, updatedAt,
                null, null, null, "METADATA", null, null);
    }
}
