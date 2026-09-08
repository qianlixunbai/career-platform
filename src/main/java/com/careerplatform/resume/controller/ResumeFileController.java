package com.careerplatform.resume.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.resume.dto.ResumeFileMetadataResponse;
import com.careerplatform.resume.dto.ResumeResponse;
import com.careerplatform.resume.dto.ResumeUploadResponse;
import com.careerplatform.resume.dto.ResumeVersionResponse;
import com.careerplatform.resume.dto.ResumeVersionUploadResponse;
import com.careerplatform.resume.entity.Resume;
import com.careerplatform.resume.entity.ResumeFile;
import com.careerplatform.resume.entity.ResumeVersion;
import com.careerplatform.resume.service.ResumeFileService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeFileController {

    private final ResumeFileService resumeFileService;

    public ResumeFileController(ResumeFileService resumeFileService) {
        this.resumeFileService = resumeFileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeUploadResponse> upload(
            @CurrentUserId Long currentUserId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("name") String name,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "versionLabel", required = false) String versionLabel) {
        ResumeFileService.UploadResult result = resumeFileService.uploadNew(
                currentUserId, file, name, description, versionLabel);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ResumeUploadResponse(
                toResumeResponse(result.resume()),
                toVersionResponse(result.version()),
                toMetadataResponse(result.file())));
    }

    @PostMapping(value = "/{resumeId}/versions/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeVersionUploadResponse> uploadVersion(
            @PathVariable Long resumeId,
            @CurrentUserId Long currentUserId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "label", required = false) String label) {
        ResumeFileService.VersionUploadResult result = resumeFileService.uploadVersion(
                resumeId, currentUserId, file, label);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ResumeVersionUploadResponse(
                toVersionResponse(result.version()),
                toMetadataResponse(result.file())));
    }

    @GetMapping("/{resumeId}/versions/{versionId}/file")
    public ResumeFileMetadataResponse metadata(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @CurrentUserId Long currentUserId) {
        return toMetadataResponse(resumeFileService.getMetadata(resumeId, versionId, currentUserId));
    }

    @GetMapping("/{resumeId}/versions/{versionId}/file/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @CurrentUserId Long currentUserId) {
        ResumeFileService.FileDownload file = resumeFileService.download(resumeId, versionId, currentUserId);
        byte[] bytes = file.bytes();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(safeMediaType(file.contentType()))
                .contentLength(bytes.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(bytes));
    }

    private ResumeResponse toResumeResponse(Resume value) {
        return new ResumeResponse(value.getId(), value.getName(), value.getDescription(),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private ResumeVersionResponse toVersionResponse(ResumeVersion value) {
        return new ResumeVersionResponse(value.getId(), value.getResumeId(), value.getVersionNo(), value.getLabel(),
                value.getStatus(), value.getFinalizedAt(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private ResumeFileMetadataResponse toMetadataResponse(ResumeFile value) {
        return new ResumeFileMetadataResponse(value.getResumeVersionId(), value.getOriginalFilename(),
                value.getContentType(), value.getFileSize(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private MediaType safeMediaType(String value) {
        try {
            return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
