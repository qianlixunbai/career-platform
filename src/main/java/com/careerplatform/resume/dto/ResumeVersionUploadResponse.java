package com.careerplatform.resume.dto;

public record ResumeVersionUploadResponse(ResumeVersionResponse version,
                                          ResumeFileMetadataResponse file) {
}
