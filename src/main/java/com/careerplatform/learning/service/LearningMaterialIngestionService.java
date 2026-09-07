package com.careerplatform.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careerplatform.ai.client.EmbeddingGateway;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import com.careerplatform.ai.service.EmbeddingVectors;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.InvalidResourceStateException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.learning.dto.LearningMaterialRequest;
import com.careerplatform.learning.entity.LearningMaterial;
import com.careerplatform.learning.entity.LearningMaterialChunk;
import com.careerplatform.learning.entity.LearningPlan;
import com.careerplatform.learning.mapper.LearningMaterialChunkMapper;
import com.careerplatform.learning.mapper.LearningMaterialMapper;
import com.careerplatform.learning.mapper.LearningPlanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Owns file-backed material ingestion. Provider calls happen before the
 * persistence transaction; chunk replacement and the READY transition are
 * one short transaction guarded by the plan and material rows.
 */
@Service
public class LearningMaterialIngestionService {

    public static final String STATUS_METADATA = "METADATA";
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";
    public static final int MAX_FILES_PER_PLAN = 20;
    public static final long MAX_FILE_BYTES_PER_PLAN = 100L * 1024 * 1024;
    public static final int MAX_READY_CHUNKS_PER_PLAN = 1_000;

    private final LearningService learningService;
    private final LearningMaterialMapper materialMapper;
    private final LearningMaterialChunkMapper chunkMapper;
    private final LearningPlanMapper planMapper;
    private final MaterialDocumentParser parser;
    private final EmbeddingGateway embeddingGateway;
    private final TransactionTemplate indexTransaction;

    @Autowired
    public LearningMaterialIngestionService(
            LearningService learningService,
            LearningMaterialMapper materialMapper,
            LearningMaterialChunkMapper chunkMapper,
            LearningPlanMapper planMapper,
            MaterialDocumentParser parser,
            EmbeddingGateway embeddingGateway,
            PlatformTransactionManager transactionManager) {
        this(learningService, materialMapper, chunkMapper, planMapper, parser, embeddingGateway,
                new TransactionTemplate(transactionManager));
    }

    /** Package-private seam for deterministic service tests. */
    LearningMaterialIngestionService(
            LearningService learningService,
            LearningMaterialMapper materialMapper,
            LearningMaterialChunkMapper chunkMapper,
            LearningPlanMapper planMapper,
            MaterialDocumentParser parser,
            EmbeddingGateway embeddingGateway,
            TransactionTemplate indexTransaction) {
        this.learningService = Objects.requireNonNull(learningService, "learningService must not be null");
        this.materialMapper = Objects.requireNonNull(materialMapper, "materialMapper must not be null");
        this.chunkMapper = Objects.requireNonNull(chunkMapper, "chunkMapper must not be null");
        this.planMapper = Objects.requireNonNull(planMapper, "planMapper must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.embeddingGateway = Objects.requireNonNull(embeddingGateway, "embeddingGateway must not be null");
        this.indexTransaction = Objects.requireNonNull(indexTransaction, "indexTransaction must not be null");
    }

    /**
     * Creates metadata through the existing LearningService owner boundary,
     * persists the original bytes, and synchronously builds its index.
     */
    public LearningMaterial upload(Long planId, Long userId, MultipartFile file, Long taskId) {
        // Owner checks intentionally happen before reading or parsing untrusted bytes.
        learningService.getPlan(planId, userId);
        if (taskId != null) {
            learningService.getTask(planId, taskId, userId);
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("上传文件不能为空");
        }

        byte[] bytes = readMultipart(file);
        String originalName = file.getOriginalFilename();
        List<MaterialDocumentParser.ParsedChunk> parsed = parser.parse(
                bytes, originalName, file.getContentType());
        String storedName = safeFileName(originalName);
        String contentType = normalizeContentType(file.getContentType());

        LearningMaterialRequest request = new LearningMaterialRequest();
        request.setTaskId(taskId);
        request.setTitle(titleFor(storedName));
        LearningMaterial material = persistUploadedMaterial(
                planId, userId, request, storedName, contentType, bytes);

        try {
            index(material.getId(), planId, userId, bytes, parsed);
        } catch (RuntimeException exception) {
            markFailedAfterUpload(material.getId(), planId, userId);
            throw safeIndexFailure(exception);
        }
        return learningService.getMaterial(planId, material.getId(), userId);
    }

    /** Rebuilds an existing original; a READY index remains visible on failure. */
    public LearningMaterial reindex(Long planId, Long materialId, Long userId) {
        learningService.getPlan(planId, userId);
        LearningMaterial material = learningService.getMaterial(planId, materialId, userId);
        byte[] bytes = materialMapper.selectOwnedFileContent(materialId, planId, userId);
        if (bytes == null || bytes.length == 0 || material.getFileName() == null
                || material.getContentType() == null) {
            throw new InvalidResourceStateException("资料原件不可用，请重新上传");
        }
        List<MaterialDocumentParser.ParsedChunk> parsed = parser.parse(
                bytes, material.getFileName(), material.getContentType());
        try {
            index(materialId, planId, userId, bytes, parsed);
        } catch (RuntimeException exception) {
            if (!STATUS_READY.equals(material.getIndexStatus())) {
                markFailedAfterUpload(materialId, planId, userId);
            }
            throw safeIndexFailure(exception);
        }
        return learningService.getMaterial(planId, materialId, userId);
    }

    /** Owner-checked original download payload for the controller. */
    public FileDownload download(Long planId, Long materialId, Long userId) {
        learningService.getPlan(planId, userId);
        LearningMaterial material = learningService.getMaterial(planId, materialId, userId);
        byte[] bytes = materialMapper.selectOwnedFileContent(materialId, planId, userId);
        if (bytes == null || bytes.length == 0) {
            throw new InvalidResourceStateException("资料原件不可用，请重新上传");
        }
        return new FileDownload(
                safeFileName(material.getFileName()),
                normalizeContentType(material.getContentType()),
                bytes);
    }

    private void index(Long materialId, Long planId, Long userId, byte[] expectedBytes,
                       List<MaterialDocumentParser.ParsedChunk> parsed) {
        if (!embeddingGateway.isAvailable()) {
            throw new AiServiceUnavailableException("学习资料向量服务当前未配置");
        }
        String identity = safeIdentity(embeddingGateway.identity());
        List<String> texts = parsed.stream().map(MaterialDocumentParser.ParsedChunk::text).toList();
        List<float[]> vectors;
        try {
            vectors = embeddingGateway.embed(texts);
        } catch (AiServiceUnavailableException | AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderException("学习资料向量服务暂时不可用");
        }
        if (vectors == null || vectors.size() != parsed.size()) {
            throw new AiProviderException("学习资料向量服务返回内容无效");
        }

        List<String> encoded = new ArrayList<>(vectors.size());
        try {
            int dimension = -1;
            for (float[] vector : vectors) {
                EmbeddingVectors.validate(vector);
                if (dimension < 0) {
                    dimension = vector.length;
                } else if (vector.length != dimension) {
                    throw new AiProviderException("学习资料向量服务返回内容无效");
                }
                encoded.add(EmbeddingVectors.encode(vector));
            }
        } catch (RuntimeException exception) {
            if (exception instanceof AiProviderException providerException) {
                throw providerException;
            }
            throw new AiProviderException("学习资料向量服务返回内容无效");
        }

        String expectedDigest = digest(expectedBytes);
        indexTransaction.executeWithoutResult(transaction -> {
            LearningPlan plan = planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                    .eq(LearningPlan::getId, planId)
                    .eq(LearningPlan::getUserId, userId)
                    .last("FOR UPDATE"));
            if (plan == null) {
                throw notFound();
            }
            LearningMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<LearningMaterial>()
                    .eq(LearningMaterial::getId, materialId)
                    .eq(LearningMaterial::getPlanId, planId)
                    .eq(LearningMaterial::getUserId, userId)
                    .last("FOR UPDATE"));
            if (material == null) {
                throw notFound();
            }
            byte[] currentBytes = materialMapper.selectOwnedFileContent(materialId, planId, userId);
            if (currentBytes == null || !expectedDigest.equals(digest(currentBytes))) {
                throw new InvalidResourceStateException("资料在索引期间已更新，请重试");
            }

            long existingChunks = chunkMapper.countReadyChunksExcluding(planId, userId, materialId);
            if (existingChunks + parsed.size() > MAX_READY_CHUNKS_PER_PLAN) {
                throw new InvalidResourceStateException("学习资料分片数量超过计划上限，请删除资料后重试");
            }

            chunkMapper.delete(new LambdaQueryWrapper<LearningMaterialChunk>()
                    .eq(LearningMaterialChunk::getMaterialId, materialId)
                    .eq(LearningMaterialChunk::getUserId, userId));
            for (int i = 0; i < parsed.size(); i++) {
                MaterialDocumentParser.ParsedChunk source = parsed.get(i);
                LearningMaterialChunk chunk = new LearningMaterialChunk();
                chunk.setUserId(userId);
                chunk.setMaterialId(materialId);
                chunk.setChunkIndex(source.chunkIndex());
                chunk.setText(source.text());
                chunk.setLocationLabel(source.locationLabel());
                chunk.setPageNumber(source.pageNumber());
                chunk.setEmbedding(encoded.get(i));
                chunk.setEmbeddingIdentity(identity);
                chunkMapper.insert(chunk);
            }
            int affected = materialMapper.update(null, new LambdaUpdateWrapper<LearningMaterial>()
                    .set(LearningMaterial::getIndexStatus, STATUS_READY)
                    .set(LearningMaterial::getChunkCount, parsed.size())
                    .set(LearningMaterial::getEmbeddingIdentity, identity)
                    .eq(LearningMaterial::getId, materialId)
                    .eq(LearningMaterial::getPlanId, planId)
                    .eq(LearningMaterial::getUserId, userId));
            if (affected != 1) {
                throw notFound();
            }
        });
    }

    private LearningMaterial persistUploadedMaterial(Long planId, Long userId,
                                                     LearningMaterialRequest request,
                                                     String fileName, String contentType,
                                                     byte[] bytes) {
        return indexTransaction.execute(status -> {
            LearningPlan plan = planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                    .eq(LearningPlan::getId, planId)
                    .eq(LearningPlan::getUserId, userId)
                    .last("FOR UPDATE"));
            if (plan == null) {
                throw notFound();
            }
            if (materialMapper.countUploadedInPlan(planId, userId) >= MAX_FILES_PER_PLAN
                    || materialMapper.sumUploadedBytesInPlan(planId, userId)
                    > MAX_FILE_BYTES_PER_PLAN - bytes.length) {
                throw new InvalidRequestException("每个学习计划最多上传20份资料且总大小不能超过100MiB");
            }
            LearningMaterial material = learningService.createMaterial(planId, userId, request);
            markUploaded(material.getId(), planId, userId, fileName, contentType, bytes);
            return material;
        });
    }

    private void markUploaded(Long materialId, Long planId, Long userId,
                              String fileName, String contentType, byte[] bytes) {
        int affected = materialMapper.update(null, new LambdaUpdateWrapper<LearningMaterial>()
                .set(LearningMaterial::getFileContent, bytes)
                .set(LearningMaterial::getFileName, fileName)
                .set(LearningMaterial::getContentType, contentType)
                .set(LearningMaterial::getFileSize, (long) bytes.length)
                .set(LearningMaterial::getIndexStatus, STATUS_UPLOADED)
                .set(LearningMaterial::getChunkCount, null)
                .set(LearningMaterial::getEmbeddingIdentity, null)
                .eq(LearningMaterial::getId, materialId)
                .eq(LearningMaterial::getPlanId, planId)
                .eq(LearningMaterial::getUserId, userId));
        if (affected != 1) {
            throw notFound();
        }
    }

    private void markFailedAfterUpload(Long materialId, Long planId, Long userId) {
        try {
            materialMapper.update(null, new LambdaUpdateWrapper<LearningMaterial>()
                    .set(LearningMaterial::getIndexStatus, STATUS_FAILED)
                    .eq(LearningMaterial::getId, materialId)
                    .eq(LearningMaterial::getPlanId, planId)
                    .eq(LearningMaterial::getUserId, userId)
                    .ne(LearningMaterial::getIndexStatus, STATUS_READY));
        } catch (RuntimeException ignored) {
            // Preserve the original safe provider/state failure for the client.
        }
    }

    private byte[] readMultipart(MultipartFile file) {
        if (file.getSize() > MaterialDocumentParser.MAX_FILE_SIZE) {
            throw new InvalidRequestException("文件大小不能超过5MiB");
        }
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MaterialDocumentParser.MAX_FILE_SIZE + 1);
            if (bytes.length > MaterialDocumentParser.MAX_FILE_SIZE) throw new InvalidRequestException("文件大小不能超过5MiB");
            return bytes;
        } catch (IOException exception) {
            throw new InvalidRequestException("上传文件读取失败");
        }
    }

    private static String safeFileName(String filename) {
        String value = filename == null ? "" : filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        value = slash >= 0 ? value.substring(slash + 1) : value;
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isEmpty()) {
            throw new InvalidRequestException("文件名无效");
        }
        if (value.length() <= 255) {
            return value;
        }
        int dot = value.lastIndexOf('.');
        String extension = dot > 0 ? value.substring(dot) : "";
        int prefixLength = Math.max(1, 255 - extension.length());
        return value.substring(0, prefixLength) + extension;
    }

    private static String titleFor(String filename) {
        if (filename.length() <= 200) {
            return filename;
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot > 0 ? filename.substring(dot) : "";
        return filename.substring(0, Math.max(1, 200 - extension.length())) + extension;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        int separator = contentType.indexOf(';');
        String value = separator < 0 ? contentType : contentType.substring(0, separator);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeIdentity(String identity) {
        if (identity == null || identity.isBlank() || identity.length() > 128) {
            throw new AiProviderException("学习资料向量服务返回内容无效");
        }
        return identity;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static RuntimeException safeIndexFailure(RuntimeException exception) {
        if (exception instanceof AiServiceUnavailableException
                || exception instanceof AiProviderException
                || exception instanceof InvalidResourceStateException
                || exception instanceof ResourceNotFoundException) {
            return exception;
        }
        return new AiProviderException("学习资料索引失败，请稍后重试");
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("资源不存在");
    }

    public record FileDownload(String fileName, String contentType, byte[] bytes) {
        public FileDownload {
            bytes = bytes == null ? null : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }
}
