package com.careerplatform.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careerplatform.common.exception.DuplicateResourceException;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.profile.dto.CertificateAwardRequest;
import com.careerplatform.profile.dto.EducationExperienceRequest;
import com.careerplatform.profile.dto.InternshipExperienceRequest;
import com.careerplatform.profile.dto.ProfileRequest;
import com.careerplatform.profile.dto.ProjectExperienceRequest;
import com.careerplatform.profile.entity.CertificateAward;
import com.careerplatform.profile.entity.EducationExperience;
import com.careerplatform.profile.entity.InternshipExperience;
import com.careerplatform.profile.entity.ProjectExperience;
import com.careerplatform.profile.entity.Skill;
import com.careerplatform.profile.entity.UserProfile;
import com.careerplatform.profile.entity.UserSkill;
import com.careerplatform.profile.enums.Proficiency;
import com.careerplatform.profile.mapper.CertificateAwardMapper;
import com.careerplatform.profile.mapper.EducationExperienceMapper;
import com.careerplatform.profile.mapper.InternshipExperienceMapper;
import com.careerplatform.profile.mapper.ProjectExperienceMapper;
import com.careerplatform.profile.mapper.SkillMapper;
import com.careerplatform.profile.mapper.UserProfileMapper;
import com.careerplatform.profile.mapper.UserSkillMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ProfileService {

    private final UserProfileMapper userProfileMapper;
    private final EducationExperienceMapper educationExperienceMapper;
    private final SkillMapper skillMapper;
    private final UserSkillMapper userSkillMapper;
    private final ProjectExperienceMapper projectExperienceMapper;
    private final InternshipExperienceMapper internshipExperienceMapper;
    private final CertificateAwardMapper certificateAwardMapper;

    public ProfileService(UserProfileMapper userProfileMapper, EducationExperienceMapper educationExperienceMapper,
                          SkillMapper skillMapper, UserSkillMapper userSkillMapper,
                          ProjectExperienceMapper projectExperienceMapper,
                          InternshipExperienceMapper internshipExperienceMapper,
                          CertificateAwardMapper certificateAwardMapper) {
        this.userProfileMapper = userProfileMapper;
        this.educationExperienceMapper = educationExperienceMapper;
        this.skillMapper = skillMapper;
        this.userSkillMapper = userSkillMapper;
        this.projectExperienceMapper = projectExperienceMapper;
        this.internshipExperienceMapper = internshipExperienceMapper;
        this.certificateAwardMapper = certificateAwardMapper;
    }

    public UserProfile getProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        if (profile == null) {
            throw notFound();
        }
        return profile;
    }

    @Transactional
    public UserProfile upsertProfile(Long userId, ProfileRequest request) {
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            apply(profile, request);
            try {
                userProfileMapper.insert(profile);
                return profile;
            } catch (DuplicateKeyException exception) {
                profile = getProfile(userId);
            }
        }
        apply(profile, request);
        userProfileMapper.update(profile, new LambdaUpdateWrapper<UserProfile>()
                .eq(UserProfile::getId, profile.getId())
                .eq(UserProfile::getUserId, userId));
        return profile;
    }

    @Transactional
    public EducationExperience createEducation(Long userId, EducationExperienceRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate());
        EducationExperience education = new EducationExperience();
        education.setUserId(userId);
        apply(education, request);
        educationExperienceMapper.insert(education);
        return education;
    }

    public List<EducationExperience> listEducation(Long userId) {
        return educationExperienceMapper.selectList(new LambdaQueryWrapper<EducationExperience>()
                .eq(EducationExperience::getUserId, userId).orderByDesc(EducationExperience::getId));
    }

    /**
     * Read the profile facts used by resume generation in deterministic source-id order.
     * A missing profile is valid; every collection in the snapshot is always non-null.
     */
    @Transactional(readOnly = true)
    public ProfileSnapshot getResumeSnapshot(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        List<EducationExperience> educationExperiences = safeList(educationExperienceMapper.selectList(
                new LambdaQueryWrapper<EducationExperience>()
                        .eq(EducationExperience::getUserId, userId)
                        .orderByAsc(EducationExperience::getId)));
        List<UserSkillDetail> userSkills = safeList(userSkillMapper.selectList(new LambdaQueryWrapper<UserSkill>()
                        .eq(UserSkill::getUserId, userId)
                        .orderByAsc(UserSkill::getId)))
                .stream()
                .map(userSkill -> new UserSkillDetail(userSkill, getSkill(userSkill.getSkillId())))
                .toList();
        List<ProjectExperience> projectExperiences = safeList(projectExperienceMapper.selectList(
                new LambdaQueryWrapper<ProjectExperience>()
                        .eq(ProjectExperience::getUserId, userId)
                        .orderByAsc(ProjectExperience::getId)));
        List<InternshipExperience> internshipExperiences = safeList(internshipExperienceMapper.selectList(
                new LambdaQueryWrapper<InternshipExperience>()
                        .eq(InternshipExperience::getUserId, userId)
                        .orderByAsc(InternshipExperience::getId)));
        List<CertificateAward> certificateAwards = safeList(certificateAwardMapper.selectList(
                new LambdaQueryWrapper<CertificateAward>()
                        .eq(CertificateAward::getUserId, userId)
                        .orderByAsc(CertificateAward::getId)));
        return new ProfileSnapshot(profile, educationExperiences, userSkills,
                projectExperiences, internshipExperiences, certificateAwards);
    }

    public EducationExperience getEducation(Long id, Long userId) {
        EducationExperience education = educationExperienceMapper.selectOne(new LambdaQueryWrapper<EducationExperience>()
                .eq(EducationExperience::getId, id).eq(EducationExperience::getUserId, userId));
        if (education == null) { throw notFound(); }
        return education;
    }

    @Transactional
    public EducationExperience updateEducation(Long id, Long userId, EducationExperienceRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate());
        EducationExperience education = getEducation(id, userId);
        apply(education, request);
        educationExperienceMapper.update(education, new LambdaUpdateWrapper<EducationExperience>()
                .eq(EducationExperience::getId, id).eq(EducationExperience::getUserId, userId));
        return education;
    }

    @Transactional
    public void deleteEducation(Long id, Long userId) {
        deleteOrNotFound(educationExperienceMapper.delete(new LambdaQueryWrapper<EducationExperience>()
                .eq(EducationExperience::getId, id).eq(EducationExperience::getUserId, userId)));
    }

    @Transactional
    public Skill createSkill(String name) {
        Skill skill = new Skill();
        skill.setName(name.trim());
        try {
            skillMapper.insert(skill);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateResourceException("资源已存在");
        }
        return skill;
    }

    public List<Skill> listSkills() {
        return skillMapper.selectList(new LambdaQueryWrapper<Skill>().orderByAsc(Skill::getName));
    }

    public Skill getSkill(Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) { throw notFound(); }
        return skill;
    }

    @Transactional
    public UserSkillDetail createUserSkill(Long userId, Long skillId, Proficiency proficiency) {
        Skill skill = getSkill(skillId);
        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkillId(skillId);
        userSkill.setProficiency(proficiency);
        try {
            userSkillMapper.insert(userSkill);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateResourceException("资源已存在");
        }
        return new UserSkillDetail(userSkill, skill);
    }

    public List<UserSkillDetail> listUserSkills(Long userId) {
        return userSkillMapper.selectList(new LambdaQueryWrapper<UserSkill>()
                        .eq(UserSkill::getUserId, userId).orderByDesc(UserSkill::getId))
                .stream().map(userSkill -> new UserSkillDetail(userSkill, getSkill(userSkill.getSkillId()))).toList();
    }

    public UserSkillDetail getUserSkill(Long id, Long userId) {
        UserSkill userSkill = userSkillMapper.selectOne(new LambdaQueryWrapper<UserSkill>()
                .eq(UserSkill::getId, id).eq(UserSkill::getUserId, userId));
        if (userSkill == null) { throw notFound(); }
        return new UserSkillDetail(userSkill, getSkill(userSkill.getSkillId()));
    }

    @Transactional
    public UserSkillDetail updateUserSkill(Long id, Long userId, Proficiency proficiency) {
        UserSkill userSkill = getUserSkill(id, userId).userSkill();
        userSkill.setProficiency(proficiency);
        userSkillMapper.update(userSkill, new LambdaUpdateWrapper<UserSkill>()
                .eq(UserSkill::getId, id).eq(UserSkill::getUserId, userId));
        return new UserSkillDetail(userSkill, getSkill(userSkill.getSkillId()));
    }

    @Transactional
    public void deleteUserSkill(Long id, Long userId) {
        deleteOrNotFound(userSkillMapper.delete(new LambdaQueryWrapper<UserSkill>()
                .eq(UserSkill::getId, id).eq(UserSkill::getUserId, userId)));
    }

    @Transactional
    public ProjectExperience createProject(Long userId, ProjectExperienceRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate());
        ProjectExperience project = new ProjectExperience(); project.setUserId(userId); apply(project, request);
        projectExperienceMapper.insert(project); return project;
    }
    public List<ProjectExperience> listProjects(Long userId) { return projectExperienceMapper.selectList(new LambdaQueryWrapper<ProjectExperience>().eq(ProjectExperience::getUserId, userId).orderByDesc(ProjectExperience::getId)); }
    public ProjectExperience getProject(Long id, Long userId) { ProjectExperience project = projectExperienceMapper.selectOne(new LambdaQueryWrapper<ProjectExperience>().eq(ProjectExperience::getId, id).eq(ProjectExperience::getUserId, userId)); if (project == null) throw notFound(); return project; }
    @Transactional public ProjectExperience updateProject(Long id, Long userId, ProjectExperienceRequest request) { validateDateRange(request.getStartDate(), request.getEndDate()); ProjectExperience project = getProject(id, userId); apply(project, request); projectExperienceMapper.update(project, new LambdaUpdateWrapper<ProjectExperience>().eq(ProjectExperience::getId, id).eq(ProjectExperience::getUserId, userId)); return project; }
    @Transactional public void deleteProject(Long id, Long userId) { deleteOrNotFound(projectExperienceMapper.delete(new LambdaQueryWrapper<ProjectExperience>().eq(ProjectExperience::getId, id).eq(ProjectExperience::getUserId, userId))); }

    @Transactional
    public InternshipExperience createInternship(Long userId, InternshipExperienceRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate());
        InternshipExperience internship = new InternshipExperience(); internship.setUserId(userId); apply(internship, request);
        internshipExperienceMapper.insert(internship); return internship;
    }
    public List<InternshipExperience> listInternships(Long userId) { return internshipExperienceMapper.selectList(new LambdaQueryWrapper<InternshipExperience>().eq(InternshipExperience::getUserId, userId).orderByDesc(InternshipExperience::getId)); }
    public InternshipExperience getInternship(Long id, Long userId) { InternshipExperience internship = internshipExperienceMapper.selectOne(new LambdaQueryWrapper<InternshipExperience>().eq(InternshipExperience::getId, id).eq(InternshipExperience::getUserId, userId)); if (internship == null) throw notFound(); return internship; }
    @Transactional public InternshipExperience updateInternship(Long id, Long userId, InternshipExperienceRequest request) { validateDateRange(request.getStartDate(), request.getEndDate()); InternshipExperience internship = getInternship(id, userId); apply(internship, request); internshipExperienceMapper.update(internship, new LambdaUpdateWrapper<InternshipExperience>().eq(InternshipExperience::getId, id).eq(InternshipExperience::getUserId, userId)); return internship; }
    @Transactional public void deleteInternship(Long id, Long userId) { deleteOrNotFound(internshipExperienceMapper.delete(new LambdaQueryWrapper<InternshipExperience>().eq(InternshipExperience::getId, id).eq(InternshipExperience::getUserId, userId))); }

    @Transactional public CertificateAward createCertificate(Long userId, CertificateAwardRequest request) { CertificateAward certificate = new CertificateAward(); certificate.setUserId(userId); apply(certificate, request); certificateAwardMapper.insert(certificate); return certificate; }
    public List<CertificateAward> listCertificates(Long userId) { return certificateAwardMapper.selectList(new LambdaQueryWrapper<CertificateAward>().eq(CertificateAward::getUserId, userId).orderByDesc(CertificateAward::getId)); }
    public CertificateAward getCertificate(Long id, Long userId) { CertificateAward certificate = certificateAwardMapper.selectOne(new LambdaQueryWrapper<CertificateAward>().eq(CertificateAward::getId, id).eq(CertificateAward::getUserId, userId)); if (certificate == null) throw notFound(); return certificate; }
    @Transactional public CertificateAward updateCertificate(Long id, Long userId, CertificateAwardRequest request) { CertificateAward certificate = getCertificate(id, userId); apply(certificate, request); certificateAwardMapper.update(certificate, new LambdaUpdateWrapper<CertificateAward>().eq(CertificateAward::getId, id).eq(CertificateAward::getUserId, userId)); return certificate; }
    @Transactional public void deleteCertificate(Long id, Long userId) { deleteOrNotFound(certificateAwardMapper.delete(new LambdaQueryWrapper<CertificateAward>().eq(CertificateAward::getId, id).eq(CertificateAward::getUserId, userId))); }

    private void apply(UserProfile entity, ProfileRequest request) { entity.setFullName(request.getFullName()); entity.setPhone(request.getPhone()); entity.setEmail(request.getEmail()); entity.setCurrentCity(request.getCurrentCity()); entity.setPersonalWebsite(request.getPersonalWebsite()); entity.setGithubUrl(request.getGithubUrl()); }
    private void apply(EducationExperience entity, EducationExperienceRequest request) { entity.setSchoolName(request.getSchoolName()); entity.setMajor(request.getMajor()); entity.setDegree(request.getDegree()); entity.setStartDate(request.getStartDate()); entity.setEndDate(request.getEndDate()); entity.setDescription(request.getDescription()); }
    private void apply(ProjectExperience entity, ProjectExperienceRequest request) { entity.setProjectName(request.getProjectName()); entity.setRole(request.getRole()); entity.setStartDate(request.getStartDate()); entity.setEndDate(request.getEndDate()); entity.setDescription(request.getDescription()); entity.setTechStack(request.getTechStack()); entity.setProjectUrl(request.getProjectUrl()); }
    private void apply(InternshipExperience entity, InternshipExperienceRequest request) { entity.setCompanyName(request.getCompanyName()); entity.setPosition(request.getPosition()); entity.setStartDate(request.getStartDate()); entity.setEndDate(request.getEndDate()); entity.setDescription(request.getDescription()); }
    private void apply(CertificateAward entity, CertificateAwardRequest request) { entity.setName(request.getName()); entity.setType(request.getType()); entity.setIssuer(request.getIssuer()); entity.setIssueDate(request.getIssueDate()); entity.setDescription(request.getDescription()); }
    private void validateDateRange(LocalDate startDate, LocalDate endDate) { if (endDate != null && endDate.isBefore(startDate)) throw new InvalidRequestException("结束日期不能早于开始日期"); }
    private void deleteOrNotFound(int affectedRows) { if (affectedRows == 0) throw notFound(); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("资源不存在"); }

    private <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }

    public record UserSkillDetail(UserSkill userSkill, Skill skill) { }

    public record ProfileSnapshot(UserProfile profile,
                                  List<EducationExperience> educationExperiences,
                                  List<UserSkillDetail> userSkills,
                                  List<ProjectExperience> projectExperiences,
                                  List<InternshipExperience> internshipExperiences,
                                  List<CertificateAward> certificateAwards) {
        public ProfileSnapshot {
            educationExperiences = educationExperiences == null ? List.of() : List.copyOf(educationExperiences);
            userSkills = userSkills == null ? List.of() : List.copyOf(userSkills);
            projectExperiences = projectExperiences == null ? List.of() : List.copyOf(projectExperiences);
            internshipExperiences = internshipExperiences == null ? List.of() : List.copyOf(internshipExperiences);
            certificateAwards = certificateAwards == null ? List.of() : List.copyOf(certificateAwards);
        }
    }
}
