package com.careerplatform.career.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careerplatform.career.dto.CareerGoalRequest;
import com.careerplatform.career.dto.CompanyRequest;
import com.careerplatform.career.dto.JobNoteRequest;
import com.careerplatform.career.dto.JobRequirementRequest;
import com.careerplatform.career.dto.JobRequest;
import com.careerplatform.career.entity.CareerGoal;
import com.careerplatform.career.entity.Company;
import com.careerplatform.career.entity.Job;
import com.careerplatform.career.entity.JobNote;
import com.careerplatform.career.entity.JobRequirement;
import com.careerplatform.career.enums.RequirementType;
import com.careerplatform.career.mapper.CareerGoalMapper;
import com.careerplatform.career.mapper.CompanyMapper;
import com.careerplatform.career.mapper.JobMapper;
import com.careerplatform.career.mapper.JobNoteMapper;
import com.careerplatform.career.mapper.JobRequirementMapper;
import com.careerplatform.application.entity.Application;
import com.careerplatform.application.mapper.ApplicationMapper;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.ResourceInUseException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.profile.entity.Skill;
import com.careerplatform.profile.mapper.SkillMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CareerService {

    private final CareerGoalMapper careerGoalMapper;
    private final CompanyMapper companyMapper;
    private final JobMapper jobMapper;
    private final JobRequirementMapper jobRequirementMapper;
    private final JobNoteMapper jobNoteMapper;
    private final SkillMapper skillMapper;
    private final ApplicationMapper applicationMapper;

    public CareerService(CareerGoalMapper careerGoalMapper, CompanyMapper companyMapper, JobMapper jobMapper,
                         JobRequirementMapper jobRequirementMapper, JobNoteMapper jobNoteMapper,
                         SkillMapper skillMapper, ApplicationMapper applicationMapper) {
        this.careerGoalMapper = careerGoalMapper;
        this.companyMapper = companyMapper;
        this.jobMapper = jobMapper;
        this.jobRequirementMapper = jobRequirementMapper;
        this.jobNoteMapper = jobNoteMapper;
        this.skillMapper = skillMapper;
        this.applicationMapper = applicationMapper;
    }

    @Transactional
    public CareerGoal createGoal(Long userId, CareerGoalRequest request) {
        CareerGoal goal = new CareerGoal();
        goal.setUserId(userId);
        apply(goal, request);
        careerGoalMapper.insert(goal);
        return goal;
    }

    public List<CareerGoal> listGoals(Long userId) {
        return careerGoalMapper.selectList(new LambdaQueryWrapper<CareerGoal>()
                .eq(CareerGoal::getUserId, userId).orderByDesc(CareerGoal::getId));
    }

    public CareerGoal getGoal(Long id, Long userId) {
        CareerGoal goal = careerGoalMapper.selectOne(new LambdaQueryWrapper<CareerGoal>()
                .eq(CareerGoal::getId, id).eq(CareerGoal::getUserId, userId));
        if (goal == null) { throw notFound(); }
        return goal;
    }

    @Transactional
    public CareerGoal updateGoal(Long id, Long userId, CareerGoalRequest request) {
        CareerGoal goal = getGoal(id, userId);
        apply(goal, request);
        updateOrNotFound(careerGoalMapper.update(goal, new LambdaUpdateWrapper<CareerGoal>()
                .eq(CareerGoal::getId, id).eq(CareerGoal::getUserId, userId)));
        return goal;
    }

    @Transactional
    public void deleteGoal(Long id, Long userId) {
        deleteOrNotFound(careerGoalMapper.delete(new LambdaQueryWrapper<CareerGoal>()
                .eq(CareerGoal::getId, id).eq(CareerGoal::getUserId, userId)));
    }

    @Transactional
    public Company createCompany(Long userId, CompanyRequest request) {
        Company company = new Company();
        company.setUserId(userId);
        apply(company, request);
        companyMapper.insert(company);
        return company;
    }

    public List<Company> listCompanies(Long userId) {
        return companyMapper.selectList(new LambdaQueryWrapper<Company>()
                .eq(Company::getUserId, userId).orderByDesc(Company::getId));
    }

    public Company getCompany(Long id, Long userId) {
        Company company = companyMapper.selectOne(new LambdaQueryWrapper<Company>()
                .eq(Company::getId, id).eq(Company::getUserId, userId));
        if (company == null) { throw notFound(); }
        return company;
    }

    @Transactional
    public Company updateCompany(Long id, Long userId, CompanyRequest request) {
        Company company = getCompany(id, userId);
        apply(company, request);
        updateOrNotFound(companyMapper.update(company, new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, id).eq(Company::getUserId, userId)));
        return company;
    }

    @Transactional
    public void deleteCompany(Long id, Long userId) {
        getCompany(id, userId);
        Long jobs = jobMapper.selectCount(new LambdaQueryWrapper<Job>().eq(Job::getCompanyId, id));
        if (jobs > 0) { throw new ResourceInUseException("公司仍被岗位引用"); }
        try {
            deleteOrNotFound(companyMapper.delete(new LambdaQueryWrapper<Company>()
                    .eq(Company::getId, id).eq(Company::getUserId, userId)));
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceInUseException("公司仍被岗位引用");
        }
    }

    @Transactional
    public Job createJob(Long userId, JobRequest request) {
        validateJobDates(request.getPublishDate(), request.getDeadline());
        requireOwnedCompany(request.getCompanyId(), userId);
        Job job = new Job();
        job.setUserId(userId);
        job.setArchived(false);
        apply(job, request);
        jobMapper.insert(job);
        return job;
    }

    public List<Job> listJobs(Long userId, boolean archived) {
        return jobMapper.selectList(new LambdaQueryWrapper<Job>().eq(Job::getUserId, userId)
                .eq(Job::getArchived, archived).orderByDesc(Job::getId));
    }

    public Job getJob(Long id, Long userId) {
        return getOwnedJob(id, userId);
    }

    @Transactional
    public Job updateJob(Long id, Long userId, JobRequest request) {
        validateJobDates(request.getPublishDate(), request.getDeadline());
        Job job = getOwnedJob(id, userId);
        requireOwnedCompany(request.getCompanyId(), userId);
        apply(job, request);
        updateOrNotFound(jobMapper.update(job, new LambdaUpdateWrapper<Job>()
                .eq(Job::getId, id).eq(Job::getUserId, userId)));
        return job;
    }

    @Transactional
    public Job setArchived(Long id, Long userId, boolean archived) {
        Job job = getOwnedJob(id, userId);
        if (!Boolean.valueOf(archived).equals(job.getArchived())) {
            job.setArchived(archived);
            updateOrNotFound(jobMapper.update(job, new LambdaUpdateWrapper<Job>()
                    .eq(Job::getId, id).eq(Job::getUserId, userId)));
        }
        return job;
    }

    @Transactional
    public void deleteJob(Long id, Long userId) {
        Job job = jobMapper.selectOwnedForUpdate(id, userId);
        if (job == null) { throw notFound(); }
        Long applications = applicationMapper.selectCount(new LambdaQueryWrapper<Application>()
                .eq(Application::getJobId, id));
        if (applications > 0) {
            throw new ResourceInUseException("岗位已有投递历史，只能归档或隐藏");
        }
        try {
            jobNoteMapper.delete(new LambdaQueryWrapper<JobNote>().eq(JobNote::getJobId, id));
            jobRequirementMapper.delete(new LambdaQueryWrapper<JobRequirement>()
                    .eq(JobRequirement::getJobId, id));
            deleteOrNotFound(jobMapper.delete(new LambdaQueryWrapper<Job>()
                    .eq(Job::getId, id).eq(Job::getUserId, userId)));
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceInUseException("岗位仍被其他资源引用");
        }
    }

    @Transactional
    public JobRequirement createRequirement(Long jobId, Long userId, JobRequirementRequest request) {
        getOwnedJob(jobId, userId);
        validateRequirement(request);
        JobRequirement requirement = new JobRequirement();
        requirement.setJobId(jobId);
        apply(requirement, request);
        jobRequirementMapper.insert(requirement);
        return requirement;
    }

    public List<JobRequirement> listRequirements(Long jobId, Long userId) {
        getOwnedJob(jobId, userId);
        return jobRequirementMapper.selectList(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getJobId, jobId).orderByDesc(JobRequirement::getId));
    }

    public JobRequirement getRequirement(Long jobId, Long id, Long userId) {
        getOwnedJob(jobId, userId);
        JobRequirement requirement = jobRequirementMapper.selectOne(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getId, id).eq(JobRequirement::getJobId, jobId));
        if (requirement == null) { throw notFound(); }
        return requirement;
    }

    @Transactional
    public JobRequirement updateRequirement(Long jobId, Long id, Long userId, JobRequirementRequest request) {
        JobRequirement requirement = getRequirement(jobId, id, userId);
        validateRequirement(request);
        apply(requirement, request);
        updateOrNotFound(jobRequirementMapper.update(requirement, new LambdaUpdateWrapper<JobRequirement>()
                .eq(JobRequirement::getId, id).eq(JobRequirement::getJobId, jobId)));
        return requirement;
    }

    @Transactional
    public void deleteRequirement(Long jobId, Long id, Long userId) {
        getOwnedJob(jobId, userId);
        deleteOrNotFound(jobRequirementMapper.delete(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getId, id).eq(JobRequirement::getJobId, jobId)));
    }

    @Transactional
    public JobNote createNote(Long jobId, Long userId, JobNoteRequest request) {
        getOwnedJob(jobId, userId);
        JobNote note = new JobNote();
        note.setJobId(jobId);
        note.setContent(request.getContent());
        jobNoteMapper.insert(note);
        return note;
    }

    public List<JobNote> listNotes(Long jobId, Long userId) {
        getOwnedJob(jobId, userId);
        return jobNoteMapper.selectList(new LambdaQueryWrapper<JobNote>()
                .eq(JobNote::getJobId, jobId).orderByDesc(JobNote::getId));
    }

    public JobNote getNote(Long jobId, Long id, Long userId) {
        getOwnedJob(jobId, userId);
        JobNote note = jobNoteMapper.selectOne(new LambdaQueryWrapper<JobNote>()
                .eq(JobNote::getId, id).eq(JobNote::getJobId, jobId));
        if (note == null) { throw notFound(); }
        return note;
    }

    @Transactional
    public JobNote updateNote(Long jobId, Long id, Long userId, JobNoteRequest request) {
        JobNote note = getNote(jobId, id, userId);
        note.setContent(request.getContent());
        updateOrNotFound(jobNoteMapper.update(note, new LambdaUpdateWrapper<JobNote>()
                .eq(JobNote::getId, id).eq(JobNote::getJobId, jobId)));
        return note;
    }

    @Transactional
    public void deleteNote(Long jobId, Long id, Long userId) {
        getOwnedJob(jobId, userId);
        deleteOrNotFound(jobNoteMapper.delete(new LambdaQueryWrapper<JobNote>()
                .eq(JobNote::getId, id).eq(JobNote::getJobId, jobId)));
    }

    private Job getOwnedJob(Long id, Long userId) {
        Job job = jobMapper.selectOne(new LambdaQueryWrapper<Job>()
                .eq(Job::getId, id).eq(Job::getUserId, userId));
        if (job == null) { throw notFound(); }
        return job;
    }

    private Company requireOwnedCompany(Long id, Long userId) {
        return getCompany(id, userId);
    }

    private void validateRequirement(JobRequirementRequest request) {
        if (request.getRequirementType() == RequirementType.SKILL) {
            if (request.getSkillId() == null) { throw new InvalidRequestException("技能要求必须关联技能"); }
            Skill skill = skillMapper.selectById(request.getSkillId());
            if (skill == null) { throw notFound(); }
        } else if (request.getSkillId() != null) {
            throw new InvalidRequestException("非技能要求不能关联技能");
        }
    }

    private void validateJobDates(LocalDate publishDate, LocalDate deadline) {
        if (publishDate != null && deadline != null && deadline.isBefore(publishDate)) {
            throw new InvalidRequestException("截止日期不能早于发布日期");
        }
    }

    private void apply(CareerGoal entity, CareerGoalRequest request) {
        entity.setTargetPosition(request.getTargetPosition()); entity.setTargetCity(request.getTargetCity());
        entity.setTargetIndustry(request.getTargetIndustry()); entity.setTargetCompanyPreference(request.getTargetCompanyPreference());
        entity.setSalaryExpectation(request.getSalaryExpectation()); entity.setNotes(request.getNotes()); entity.setStatus(request.getStatus());
    }
    private void apply(Company entity, CompanyRequest request) {
        entity.setName(request.getName()); entity.setIndustry(request.getIndustry()); entity.setCity(request.getCity());
        entity.setWebsite(request.getWebsite()); entity.setSize(request.getSize()); entity.setNotes(request.getNotes());
    }
    private void apply(Job entity, JobRequest request) {
        entity.setCompanyId(request.getCompanyId()); entity.setTitle(request.getTitle()); entity.setCity(request.getCity());
        entity.setJobType(request.getJobType()); entity.setPublishDate(request.getPublishDate()); entity.setDeadline(request.getDeadline());
        entity.setRawJd(request.getRawJd()); entity.setSourceType(request.getSourceType());
        entity.setSourceName(request.getSourceName()); entity.setSourceUrl(request.getSourceUrl());
    }
    private void apply(JobRequirement entity, JobRequirementRequest request) {
        entity.setRequirementType(request.getRequirementType()); entity.setSkillId(request.getSkillId());
        entity.setRequirementText(request.getRequirementText());
    }
    private void updateOrNotFound(int affectedRows) { if (affectedRows == 0) { throw notFound(); } }
    private void deleteOrNotFound(int affectedRows) { if (affectedRows == 0) { throw notFound(); } }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("资源不存在"); }
}
