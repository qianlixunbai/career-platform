package com.careerplatform.resume.service;

import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.resume.dto.ResumeRequest;
import com.careerplatform.resume.dto.ResumeVersionRequest;
import com.careerplatform.resume.entity.Resume;
import com.careerplatform.resume.entity.ResumeFile;
import com.careerplatform.resume.entity.ResumeVersion;
import com.careerplatform.resume.mapper.ResumeFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeFileService {

    private final ResumeService resumeService;
    private final ResumeFileMapper resumeFileMapper;
    private final ResumeFileValidator validator;

    public ResumeFileService(ResumeService resumeService,
                             ResumeFileMapper resumeFileMapper,
                             ResumeFileValidator validator) {
        this.resumeService = resumeService;
        this.resumeFileMapper = resumeFileMapper;
        this.validator = validator;
    }

    @Transactional
    public UploadResult uploadNew(Long userId, MultipartFile multipartFile, String name,
                                  String description, String versionLabel) {
        validateResumeFields(name, description);
        validateLabel(versionLabel);
        ResumeFileValidator.ValidatedFile upload = validator.validate(multipartFile);

        ResumeRequest resumeRequest = new ResumeRequest();
        resumeRequest.setName(name);
        resumeRequest.setDescription(description);
        Resume resume = resumeService.createResume(userId, resumeRequest);

        ResumeVersionRequest versionRequest = new ResumeVersionRequest();
        versionRequest.setLabel(versionLabel);
        ResumeVersion version = resumeService.createVersion(resume.getId(), userId, versionRequest);
        ResumeFile file = persistFile(userId, version.getId(), upload);
        return new UploadResult(resume, version, file);
    }

    @Transactional
    public VersionUploadResult uploadVersion(Long resumeId, Long userId,
                                             MultipartFile multipartFile, String label) {
        validateLabel(label);
        resumeService.getResume(resumeId, userId);
        ResumeFileValidator.ValidatedFile upload = validator.validate(multipartFile);

        ResumeVersionRequest versionRequest = new ResumeVersionRequest();
        versionRequest.setLabel(label);
        ResumeVersion version = resumeService.createVersion(resumeId, userId, versionRequest);
        ResumeFile file = persistFile(userId, version.getId(), upload);
        return new VersionUploadResult(version, file);
    }

    @Transactional(readOnly = true)
    public ResumeFile getMetadata(Long resumeId, Long versionId, Long userId) {
        resumeService.getVersion(resumeId, versionId, userId);
        ResumeFile file = resumeFileMapper.selectMetadataByVersionAndUser(versionId, userId);
        if (file == null) {
            throw notFound();
        }
        return file;
    }

    @Transactional(readOnly = true)
    public FileDownload download(Long resumeId, Long versionId, Long userId) {
        resumeService.getVersion(resumeId, versionId, userId);
        ResumeFile file = resumeFileMapper.selectContentByVersionAndUser(versionId, userId);
        if (file == null || file.getFileData() == null || file.getFileData().length == 0) {
            throw notFound();
        }
        return new FileDownload(file.getOriginalFilename(), file.getContentType(), file.getFileSize(),
                file.getFileData());
    }

    private ResumeFile persistFile(Long userId, Long versionId,
                                   ResumeFileValidator.ValidatedFile upload) {
        byte[] bytes = upload.bytes();
        ResumeFile file = new ResumeFile();
        file.setUserId(userId);
        file.setResumeVersionId(versionId);
        file.setOriginalFilename(upload.originalFilename());
        file.setContentType(upload.contentType());
        file.setFileSize((long) bytes.length);
        file.setFileData(bytes);
        resumeFileMapper.insert(file);
        ResumeFile persisted = resumeFileMapper.selectContentByVersionAndUser(versionId, userId);
        if (persisted == null) {
            throw notFound();
        }
        return persisted;
    }

    private void validateResumeFields(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw invalid("简历名称不能为空");
        }
        if (name.length() > 200) {
            throw invalid("简历名称长度不能超过200个字符");
        }
        if (description != null && description.length() > 2_000) {
            throw invalid("简历描述长度不能超过2000个字符");
        }
    }

    private void validateLabel(String label) {
        if (label != null && label.length() > 200) {
            throw invalid("简历版本标签长度不能超过200个字符");
        }
    }

    private InvalidRequestException invalid(String message) {
        return new InvalidRequestException(message);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("资源不存在");
    }

    public record UploadResult(Resume resume, ResumeVersion version, ResumeFile file) { }

    public record VersionUploadResult(ResumeVersion version, ResumeFile file) { }

    public record FileDownload(String originalFilename, String contentType,
                               Long fileSize, byte[] bytes) {
        public FileDownload {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
