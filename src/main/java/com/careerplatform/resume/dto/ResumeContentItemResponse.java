package com.careerplatform.resume.dto;

import com.careerplatform.resume.enums.ResumeSectionType;
import com.careerplatform.resume.enums.ResumeSourceType;

import java.time.LocalDateTime;

public record ResumeContentItemResponse(Long id, Long versionId, ResumeSectionType sectionType,
                                        String title, String content, ResumeSourceType sourceType,
                                        Long sourceId, Integer sortOrder, LocalDateTime createdAt,
                                        LocalDateTime updatedAt) {
}
