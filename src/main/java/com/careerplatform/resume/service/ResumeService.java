package com.careerplatform.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careerplatform.common.exception.InvalidResourceStateException;
import com.careerplatform.common.exception.ResourceInUseException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.profile.service.ProfileService;
import com.careerplatform.profile.service.ProfileService.UserSkillDetail;
import com.careerplatform.profile.entity.CertificateAward;
import com.careerplatform.profile.entity.EducationExperience;
import com.careerplatform.profile.entity.InternshipExperience;
import com.careerplatform.profile.entity.ProjectExperience;
import com.careerplatform.profile.entity.Skill;
import com.careerplatform.profile.entity.UserProfile;
import com.careerplatform.profile.entity.UserSkill;
import com.careerplatform.resume.dto.ResumeContentItemRequest;
import com.careerplatform.resume.dto.ResumeRequest;
import com.careerplatform.resume.dto.ResumeVersionRequest;
import com.careerplatform.resume.entity.Resume;
import com.careerplatform.resume.entity.ResumeContentItem;
import com.careerplatform.resume.entity.ResumeVersion;
import com.careerplatform.resume.enums.ResumeSectionType;
import com.careerplatform.resume.enums.ResumeSourceType;
import com.careerplatform.resume.enums.ResumeVersionStatus;
import com.careerplatform.resume.mapper.ResumeContentItemMapper;
import com.careerplatform.resume.mapper.ResumeMapper;
import com.careerplatform.resume.mapper.ResumeVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResumeService {

    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeContentItemMapper resumeContentItemMapper;
    private final ProfileService profileService;

    public ResumeService(ResumeMapper resumeMapper,
                         ResumeVersionMapper resumeVersionMapper,
                         ResumeContentItemMapper resumeContentItemMapper,
                         ProfileService profileService) {
        this.resumeMapper = resumeMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.resumeContentItemMapper = resumeContentItemMapper;
        this.profileService = profileService;
    }

    @Transactional
    public Resume createResume(Long userId, ResumeRequest request) {
        Resume resume = new Resume();
        resume.setUserId(userId);
        apply(resume, request);
        resumeMapper.insert(resume);
        return requireOwnedResume(resume.getId(), userId);
    }

    public List<Resume> listResumes(Long userId) {
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getUpdatedAt)
                .orderByDesc(Resume::getId));
    }

    public Resume getResume(Long resumeId, Long userId) {
        return requireOwnedResume(resumeId, userId);
    }

    @Transactional
    public Resume updateResume(Long resumeId, Long userId, ResumeRequest request) {
        lockOwnedResume(resumeId, userId);
        updateOrNotFound(resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .set(Resume::getName, request.getName())
                .set(Resume::getDescription, request.getDescription())
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId)));
        return requireOwnedResume(resumeId, userId);
    }

    @Transactional
    public void deleteResume(Long resumeId, Long userId) {
        lockOwnedResume(resumeId, userId);
        List<ResumeVersion> versions = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getUserId, userId));
        if (versions.stream().anyMatch(version -> ResumeVersionStatus.FINALIZED.equals(version.getStatus()))) {
            throw new ResourceInUseException("简历包含已定稿版本");
        }

        // The parent lock protects the complete child cleanup. Delete in FK order.
        for (ResumeVersion version : versions) {
            resumeContentItemMapper.delete(new LambdaQueryWrapper<ResumeContentItem>()
                    .eq(ResumeContentItem::getVersionId, version.getId())
                    .eq(ResumeContentItem::getUserId, userId));
        }
        resumeVersionMapper.delete(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getUserId, userId));
        deleteOrNotFound(resumeMapper.delete(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId)));
    }

    @Transactional
    public ResumeVersion createVersion(Long resumeId, Long userId, ResumeVersionRequest request) {
        lockOwnedResume(resumeId, userId);
        ResumeVersion version = insertDraftVersion(resumeId, userId, request.getLabel());
        return requireOwnedVersion(resumeId, version.getId(), userId);
    }

    @Transactional
    public ResumeVersion generateVersion(Long resumeId, Long userId, ResumeVersionRequest request) {
        lockOwnedResume(resumeId, userId);
        ResumeVersion version = insertDraftVersion(resumeId, userId, request.getLabel());
        addSnapshotItems(version, userId, profileService.getResumeSnapshot(userId));
        return requireOwnedVersion(resumeId, version.getId(), userId);
    }

    public List<ResumeVersion> listVersions(Long resumeId, Long userId) {
        requireOwnedResume(resumeId, userId);
        return resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getUserId, userId)
                .orderByAsc(ResumeVersion::getVersionNo));
    }

    public ResumeVersion getVersion(Long resumeId, Long versionId, Long userId) {
        requireOwnedResume(resumeId, userId);
        return requireOwnedVersion(resumeId, versionId, userId);
    }

    @Transactional
    public ResumeVersion updateVersion(Long resumeId, Long versionId, Long userId,
                                       ResumeVersionRequest request) {
        lockOwnedResume(resumeId, userId);
        ResumeVersion version = lockOwnedVersion(resumeId, versionId, userId);
        requireDraft(version);
        updateOrNotFound(resumeVersionMapper.update(null, new LambdaUpdateWrapper<ResumeVersion>()
                .set(ResumeVersion::getLabel, request.getLabel())
                .eq(ResumeVersion::getId, versionId)
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getUserId, userId)));
        return requireOwnedVersion(resumeId, versionId, userId);
    }

    @Transactional
    public ResumeVersion copyVersion(Long resumeId, Long versionId, Long userId,
                                     ResumeVersionRequest request) {
        lockOwnedResume(resumeId, userId);
        ResumeVersion source = lockOwnedVersion(resumeId, versionId, userId);
        Integer nextVersionNo = nextVersionNo(resumeId);

        ResumeVersion target = new ResumeVersion();
        target.setUserId(userId);
        target.setResumeId(resumeId);
        target.setVersionNo(nextVersionNo);
        target.setLabel(request.getLabel());
        target.setStatus(ResumeVersionStatus.DRAFT);
        target.setFinalizedAt(null);
        resumeVersionMapper.insert(target);

        List<ResumeContentItem> sourceItems = resumeContentItemMapper.selectList(new LambdaQueryWrapper<ResumeContentItem>()
                .eq(ResumeContentItem::getVersionId, source.getId())
                .eq(ResumeContentItem::getUserId, userId)
                .orderByAsc(ResumeContentItem::getSortOrder)
                .orderByAsc(ResumeContentItem::getId));
        for (ResumeContentItem sourceItem : sourceItems) {
            ResumeContentItem copiedItem = new ResumeContentItem();
            copiedItem.setUserId(userId);
            copiedItem.setVersionId(target.getId());
            copiedItem.setSectionType(sourceItem.getSectionType());
            copiedItem.setTitle(sourceItem.getTitle());
            copiedItem.setContent(sourceItem.getContent());
            copiedItem.setSourceType(sourceItem.getSourceType());
            copiedItem.setSourceId(sourceItem.getSourceId());
            copiedItem.setSortOrder(sourceItem.getSortOrder());
            resumeContentItemMapper.insert(copiedItem);
        }
        return requireOwnedVersion(resumeId, target.getId(), userId);
    }

    @Transactional
    public ResumeVersion finalizeVersion(Long resumeId, Long versionId, Long userId) {
        lockOwnedResume(resumeId, userId);
        ResumeVersion version = lockOwnedVersion(resumeId, versionId, userId);
        if (ResumeVersionStatus.FINALIZED.equals(version.getStatus())) {
            // Idempotent finalize: do not issue an update and preserve the original timestamp.
            return version;
        }
        requireDraft(version);
        version.setStatus(ResumeVersionStatus.FINALIZED);
        version.setFinalizedAt(java.time.LocalDateTime.now());
        updateOrNotFound(resumeVersionMapper.update(null, new LambdaUpdateWrapper<ResumeVersion>()
                .set(ResumeVersion::getStatus, version.getStatus())
                .set(ResumeVersion::getFinalizedAt, version.getFinalizedAt())
                .eq(ResumeVersion::getId, versionId)
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getUserId, userId)));
        return requireOwnedVersion(resumeId, versionId, userId);
    }

    @Transactional
    public void deleteVersion(Long resumeId, Long versionId, Long userId) {
        lockOwnedResume(resumeId, userId);
        ResumeVersion version = lockOwnedVersion(resumeId, versionId, userId);
        requireDraft(version);
        resumeContentItemMapper.delete(new LambdaQueryWrapper<ResumeContentItem>()
                .eq(ResumeContentItem::getVersionId, versionId)
                .eq(ResumeContentItem::getUserId, userId));
        deleteOrNotFound(resumeVersionMapper.delete(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, versionId)
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getUserId, userId)));
    }

    @Transactional
    public ResumeContentItem createItem(Long resumeId, Long versionId, Long userId,
                                        ResumeContentItemRequest request) {
        lockOwnedResume(resumeId, userId);
        ResumeVersion version = lockOwnedVersion(resumeId, versionId, userId);
        requireDraft(version);

        ResumeContentItem item = new ResumeContentItem();
        item.setUserId(userId);
        item.setVersionId(versionId);
        item.setSectionType(request.getSectionType());
        item.setTitle(request.getTitle());
        item.setContent(request.getContent());
        item.setSortOrder(request.getSortOrder());
        // Manually-authored items never claim a profile source.
        item.setSourceType(null);
        item.setSourceId(null);
        resumeContentItemMapper.insert(item);
        return requireOwnedItem(resumeId, versionId, item.getId(), userId);
    }

    public List<ResumeContentItem> listItems(Long resumeId, Long versionId, Long userId) {
        requireOwnedResume(resumeId, userId);
        requireOwnedVersion(resumeId, versionId, userId);
        return resumeContentItemMapper.selectList(new LambdaQueryWrapper<ResumeContentItem>()
                .eq(ResumeContentItem::getVersionId, versionId)
                .eq(ResumeContentItem::getUserId, userId)
                .orderByAsc(ResumeContentItem::getSortOrder)
                .orderByAsc(ResumeContentItem::getId));
    }

    public ResumeContentItem getItem(Long resumeId, Long versionId, Long itemId, Long userId) {
        requireOwnedResume(resumeId, userId);
        requireOwnedVersion(resumeId, versionId, userId);
        return requireOwnedItem(resumeId, versionId, itemId, userId);
    }

    @Transactional
    public ResumeContentItem updateItem(Long resumeId, Long versionId, Long itemId, Long userId,
                                        ResumeContentItemRequest request) {
        lockOwnedResume(resumeId, userId);
        ResumeVersion version = lockOwnedVersion(resumeId, versionId, userId);
        requireDraft(version);
        requireOwnedItem(resumeId, versionId, itemId, userId);
        // Keep sourceType/sourceId unchanged for generated snapshot items.
        updateOrNotFound(resumeContentItemMapper.update(null, new LambdaUpdateWrapper<ResumeContentItem>()
                .set(ResumeContentItem::getSectionType, request.getSectionType())
                .set(ResumeContentItem::getTitle, request.getTitle())
                .set(ResumeContentItem::getContent, request.getContent())
                .set(ResumeContentItem::getSortOrder, request.getSortOrder())
                .eq(ResumeContentItem::getId, itemId)
                .eq(ResumeContentItem::getVersionId, versionId)
                .eq(ResumeContentItem::getUserId, userId)));
        return requireOwnedItem(resumeId, versionId, itemId, userId);
    }

    @Transactional
    public void deleteItem(Long resumeId, Long versionId, Long itemId, Long userId) {
        lockOwnedResume(resumeId, userId);
        ResumeVersion version = lockOwnedVersion(resumeId, versionId, userId);
        requireDraft(version);
        requireOwnedItem(resumeId, versionId, itemId, userId);
        deleteOrNotFound(resumeContentItemMapper.delete(new LambdaQueryWrapper<ResumeContentItem>()
                .eq(ResumeContentItem::getId, itemId)
                .eq(ResumeContentItem::getVersionId, versionId)
                .eq(ResumeContentItem::getUserId, userId)));
    }

    private ResumeVersion insertDraftVersion(Long resumeId, Long userId, String label) {
        ResumeVersion version = new ResumeVersion();
        version.setUserId(userId);
        version.setResumeId(resumeId);
        version.setVersionNo(nextVersionNo(resumeId));
        version.setLabel(label);
        version.setStatus(ResumeVersionStatus.DRAFT);
        version.setFinalizedAt(null);
        resumeVersionMapper.insert(version);
        return version;
    }

    private Integer nextVersionNo(Long resumeId) {
        Integer maxVersionNo = resumeVersionMapper.selectMaxVersionNo(resumeId);
        return (maxVersionNo == null ? 0 : maxVersionNo) + 1;
    }

    private void addSnapshotItems(ResumeVersion version, Long userId,
                                  ProfileService.ProfileSnapshot snapshot) {
        int sortOrder = 0;
        sortOrder = addProfileItem(version, userId, snapshot == null ? null : snapshot.profile(), sortOrder);
        for (EducationExperience education : safeList(snapshot == null ? null : snapshot.educationExperiences())) {
            sortOrder = addEducationItem(version, userId, education, sortOrder);
        }
        for (UserSkillDetail userSkill : safeList(snapshot == null ? null : snapshot.userSkills())) {
            sortOrder = addSkillItem(version, userId, userSkill, sortOrder);
        }
        for (ProjectExperience project : safeList(snapshot == null ? null : snapshot.projectExperiences())) {
            sortOrder = addProjectItem(version, userId, project, sortOrder);
        }
        for (InternshipExperience internship : safeList(snapshot == null ? null : snapshot.internshipExperiences())) {
            sortOrder = addInternshipItem(version, userId, internship, sortOrder);
        }
        for (CertificateAward certificate : safeList(snapshot == null ? null : snapshot.certificateAwards())) {
            sortOrder = addCertificateItem(version, userId, certificate, sortOrder);
        }
    }

    private int addProfileItem(ResumeVersion version, Long userId, UserProfile profile, int sortOrder) {
        if (profile == null) {
            return sortOrder;
        }
        StringBuilder content = new StringBuilder();
        append(content, "姓名", profile.getFullName());
        append(content, "电话", profile.getPhone());
        append(content, "头像", profile.getAvatarUrl());
        append(content, "所在城市", profile.getCurrentCity());
        append(content, "个人网站", profile.getPersonalWebsite());
        append(content, "GitHub", profile.getGithubUrl());
        if (content.isEmpty()) {
            return sortOrder;
        }
        insertSnapshotItem(version, userId, ResumeSectionType.PROFILE, ResumeSourceType.PROFILE,
                profile.getId(), valueOrDefault(profile.getFullName(), "个人信息"), content.toString(), sortOrder);
        return sortOrder + 1;
    }

    private int addEducationItem(ResumeVersion version, Long userId, EducationExperience education, int sortOrder) {
        if (education == null) {
            return sortOrder;
        }
        StringBuilder content = new StringBuilder();
        append(content, "学校", education.getSchoolName());
        append(content, "专业", education.getMajor());
        append(content, "学历", education.getDegree());
        append(content, "开始日期", education.getStartDate());
        append(content, "结束日期", education.getEndDate());
        append(content, "描述", education.getDescription());
        if (content.isEmpty()) {
            return sortOrder;
        }
        insertSnapshotItem(version, userId, ResumeSectionType.EDUCATION, ResumeSourceType.EDUCATION,
                education.getId(), clean(education.getSchoolName()), content.toString(), sortOrder);
        return sortOrder + 1;
    }

    private int addSkillItem(ResumeVersion version, Long userId, UserSkillDetail detail, int sortOrder) {
        if (detail == null) {
            return sortOrder;
        }
        UserSkill userSkill = detail.userSkill();
        Skill skill = detail.skill();
        StringBuilder content = new StringBuilder();
        append(content, "技能", skill == null ? null : skill.getName());
        append(content, "熟练度", userSkill == null ? null : userSkill.getProficiency());
        if (content.isEmpty()) {
            return sortOrder;
        }
        insertSnapshotItem(version, userId, ResumeSectionType.SKILL, ResumeSourceType.SKILL,
                userSkill == null ? null : userSkill.getId(), clean(skill == null ? null : skill.getName()),
                content.toString(), sortOrder);
        return sortOrder + 1;
    }

    private int addProjectItem(ResumeVersion version, Long userId, ProjectExperience project, int sortOrder) {
        if (project == null) {
            return sortOrder;
        }
        StringBuilder content = new StringBuilder();
        append(content, "项目", project.getProjectName());
        append(content, "角色", project.getRole());
        append(content, "开始日期", project.getStartDate());
        append(content, "结束日期", project.getEndDate());
        append(content, "描述", project.getDescription());
        append(content, "技术栈", project.getTechStack());
        append(content, "项目地址", project.getProjectUrl());
        if (content.isEmpty()) {
            return sortOrder;
        }
        insertSnapshotItem(version, userId, ResumeSectionType.PROJECT, ResumeSourceType.PROJECT,
                project.getId(), clean(project.getProjectName()), content.toString(), sortOrder);
        return sortOrder + 1;
    }

    private int addInternshipItem(ResumeVersion version, Long userId, InternshipExperience internship, int sortOrder) {
        if (internship == null) {
            return sortOrder;
        }
        StringBuilder content = new StringBuilder();
        append(content, "公司", internship.getCompanyName());
        append(content, "职位", internship.getPosition());
        append(content, "开始日期", internship.getStartDate());
        append(content, "结束日期", internship.getEndDate());
        append(content, "描述", internship.getDescription());
        if (content.isEmpty()) {
            return sortOrder;
        }
        insertSnapshotItem(version, userId, ResumeSectionType.INTERNSHIP, ResumeSourceType.INTERNSHIP,
                internship.getId(), clean(internship.getCompanyName()), content.toString(), sortOrder);
        return sortOrder + 1;
    }

    private int addCertificateItem(ResumeVersion version, Long userId, CertificateAward certificate, int sortOrder) {
        if (certificate == null) {
            return sortOrder;
        }
        StringBuilder content = new StringBuilder();
        append(content, "名称", certificate.getName());
        append(content, "类型", certificate.getType());
        append(content, "颁发机构", certificate.getIssuer());
        append(content, "日期", certificate.getIssueDate());
        append(content, "描述", certificate.getDescription());
        if (content.isEmpty()) {
            return sortOrder;
        }
        insertSnapshotItem(version, userId, ResumeSectionType.CERTIFICATE, ResumeSourceType.CERTIFICATE,
                certificate.getId(), clean(certificate.getName()), content.toString(), sortOrder);
        return sortOrder + 1;
    }

    private void insertSnapshotItem(ResumeVersion version, Long userId, ResumeSectionType sectionType,
                                    ResumeSourceType sourceType, Long sourceId, String title,
                                    String content, int sortOrder) {
        ResumeContentItem item = new ResumeContentItem();
        item.setUserId(userId);
        item.setVersionId(version.getId());
        item.setSectionType(sectionType);
        item.setTitle(title);
        item.setContent(content);
        item.setSourceType(sourceType);
        item.setSourceId(sourceId);
        item.setSortOrder(sortOrder);
        resumeContentItemMapper.insert(item);
    }

    private void append(StringBuilder content, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (!content.isEmpty()) {
            content.append('\n');
        }
        content.append(label).append(": ").append(text);
    }

    private String valueOrDefault(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned == null ? fallback : cleaned;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void apply(Resume resume, ResumeRequest request) {
        resume.setName(request.getName());
        resume.setDescription(request.getDescription());
    }

    private Resume requireOwnedResume(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw notFound();
        }
        return resume;
    }

    private Resume lockOwnedResume(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectOwnedForUpdate(resumeId, userId);
        if (resume == null) {
            throw notFound();
        }
        return resume;
    }

    private ResumeVersion requireOwnedVersion(Long resumeId, Long versionId, Long userId) {
        ResumeVersion version = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, versionId)
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getUserId, userId));
        if (version == null) {
            throw notFound();
        }
        return version;
    }

    private ResumeVersion lockOwnedVersion(Long resumeId, Long versionId, Long userId) {
        ResumeVersion version = resumeVersionMapper.selectOwnedInResumeForUpdate(versionId, resumeId, userId);
        if (version == null) {
            throw notFound();
        }
        return version;
    }

    private ResumeContentItem requireOwnedItem(Long resumeId, Long versionId, Long itemId, Long userId) {
        // The caller has already established the complete Resume -> Version ownership chain.
        ResumeContentItem item = resumeContentItemMapper.selectOne(new LambdaQueryWrapper<ResumeContentItem>()
                .eq(ResumeContentItem::getId, itemId)
                .eq(ResumeContentItem::getVersionId, versionId)
                .eq(ResumeContentItem::getUserId, userId));
        if (item == null) {
            throw notFound();
        }
        return item;
    }

    private void requireDraft(ResumeVersion version) {
        if (!ResumeVersionStatus.DRAFT.equals(version.getStatus())) {
            throw new InvalidResourceStateException("已定稿简历版本不可修改");
        }
    }

    private void updateOrNotFound(int affectedRows) {
        if (affectedRows == 0) {
            throw notFound();
        }
    }

    private void deleteOrNotFound(int affectedRows) {
        if (affectedRows == 0) {
            throw notFound();
        }
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("资源不存在");
    }
}
