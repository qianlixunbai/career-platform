package com.careerplatform.resume.dto;

public record ResumeUploadResponse(ResumeResponse resume,
                                   ResumeVersionResponse version,
                                   ResumeFileMetadataResponse file) {
}
