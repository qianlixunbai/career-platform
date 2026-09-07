package com.careerplatform.learning.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.learning.dto.LearningMaterialRequest;
import com.careerplatform.learning.dto.LearningMaterialResponse;
import com.careerplatform.learning.entity.LearningMaterial;
import com.careerplatform.learning.service.LearningMaterialIngestionService;
import com.careerplatform.learning.service.LearningService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/learning-plans/{planId}/materials")
public class LearningMaterialController {

    private final LearningService learningService;
    private final LearningMaterialIngestionService ingestionService;

    @Autowired
    public LearningMaterialController(LearningService learningService,
                                      LearningMaterialIngestionService ingestionService) {
        this.learningService = learningService;
        this.ingestionService = ingestionService;
    }

    /** Source-compatible constructor for callers that only exercise metadata CRUD. */
    public LearningMaterialController(LearningService learningService) {
        this(learningService, null);
    }

    @PostMapping
    public ResponseEntity<LearningMaterialResponse> create(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningMaterialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(learningService.createMaterial(planId, currentUserId, request)));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LearningMaterialResponse> upload(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "taskId", required = false) Long taskId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(ingestionService.upload(planId, currentUserId, file, taskId)));
    }

    @GetMapping
    public List<LearningMaterialResponse> list(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId) {
        return learningService.listMaterials(planId, currentUserId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{materialId}")
    public LearningMaterialResponse get(
            @PathVariable Long planId,
            @PathVariable Long materialId,
            @CurrentUserId Long currentUserId) {
        return toResponse(learningService.getMaterial(planId, materialId, currentUserId));
    }

    @PutMapping("/{materialId}")
    public LearningMaterialResponse update(
            @PathVariable Long planId,
            @PathVariable Long materialId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningMaterialRequest request) {
        return toResponse(learningService.updateMaterial(planId, materialId, currentUserId, request));
    }

    @DeleteMapping("/{materialId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long planId,
            @PathVariable Long materialId,
            @CurrentUserId Long currentUserId) {
        learningService.deleteMaterial(planId, materialId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{materialId}/reindex")
    public LearningMaterialResponse reindex(
            @PathVariable Long planId,
            @PathVariable Long materialId,
            @CurrentUserId Long currentUserId) {
        return toResponse(ingestionService.reindex(planId, materialId, currentUserId));
    }

    @GetMapping("/{materialId}/file")
    public ResponseEntity<Resource> download(
            @PathVariable Long planId,
            @PathVariable Long materialId,
            @CurrentUserId Long currentUserId) {
        LearningMaterialIngestionService.FileDownload file =
                ingestionService.download(planId, materialId, currentUserId);
        MediaType contentType = safeMediaType(file.contentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(file.bytes().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(file.bytes()));
    }

    private LearningMaterialResponse toResponse(LearningMaterial value) {
        return new LearningMaterialResponse(value.getId(), value.getPlanId(), value.getTaskId(), value.getTitle(),
                value.getSourceUrl(), value.getDescription(), value.getCreatedAt(), value.getUpdatedAt(),
                value.getFileName(), value.getContentType(), value.getFileSize(), value.getIndexStatus(),
                value.getChunkCount(), value.getEmbeddingIdentity());
    }

    private MediaType safeMediaType(String value) {
        try {
            return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
