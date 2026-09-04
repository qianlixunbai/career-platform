package com.careerplatform.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careerplatform.application.dto.ApplicationTransitionRequest;
import com.careerplatform.application.dto.AssessmentRequest;
import com.careerplatform.application.dto.CreateApplicationRequest;
import com.careerplatform.application.dto.FinalReviewRequest;
import com.careerplatform.application.dto.InterviewRequest;
import com.careerplatform.application.dto.OfferCreateRequest;
import com.careerplatform.application.dto.OfferUpdateRequest;
import com.careerplatform.application.entity.Application;
import com.careerplatform.application.entity.ApplicationStageHistory;
import com.careerplatform.application.entity.Assessment;
import com.careerplatform.application.entity.FinalReview;
import com.careerplatform.application.entity.Interview;
import com.careerplatform.application.entity.Offer;
import com.careerplatform.application.enums.ApplicationEndReason;
import com.careerplatform.application.enums.ApplicationStage;
import com.careerplatform.application.enums.OfferStatus;
import com.careerplatform.application.mapper.ApplicationMapper;
import com.careerplatform.application.mapper.ApplicationStageHistoryMapper;
import com.careerplatform.application.mapper.AssessmentMapper;
import com.careerplatform.application.mapper.FinalReviewMapper;
import com.careerplatform.application.mapper.InterviewMapper;
import com.careerplatform.application.mapper.OfferMapper;
import com.careerplatform.career.entity.Company;
import com.careerplatform.career.entity.Job;
import com.careerplatform.career.mapper.CompanyMapper;
import com.careerplatform.career.mapper.JobMapper;
import com.careerplatform.common.exception.DuplicateResourceException;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.InvalidResourceStateException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.resume.entity.ResumeVersion;
import com.careerplatform.resume.enums.ResumeVersionStatus;
import com.careerplatform.resume.mapper.ResumeVersionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ApplicationService {

    private static final Map<ApplicationStage, Set<ApplicationStage>> TRANSITIONS = transitions();

    private final ApplicationMapper applicationMapper;
    private final ApplicationStageHistoryMapper historyMapper;
    private final AssessmentMapper assessmentMapper;
    private final InterviewMapper interviewMapper;
    private final OfferMapper offerMapper;
    private final FinalReviewMapper finalReviewMapper;
    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final ResumeVersionMapper resumeVersionMapper;

    public ApplicationService(ApplicationMapper applicationMapper,
                              ApplicationStageHistoryMapper historyMapper,
                              AssessmentMapper assessmentMapper,
                              InterviewMapper interviewMapper,
                              OfferMapper offerMapper,
                              FinalReviewMapper finalReviewMapper,
                              JobMapper jobMapper,
                              CompanyMapper companyMapper,
                              ResumeVersionMapper resumeVersionMapper) {
        this.applicationMapper = applicationMapper;
        this.historyMapper = historyMapper;
        this.assessmentMapper = assessmentMapper;
        this.interviewMapper = interviewMapper;
        this.offerMapper = offerMapper;
        this.finalReviewMapper = finalReviewMapper;
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
        this.resumeVersionMapper = resumeVersionMapper;
    }

    @Transactional
    public Application createApplication(Long userId, CreateApplicationRequest request) {
        // This deterministic parent lock serializes creates and physical deletion for one job.
        Job job = jobMapper.selectOwnedForUpdate(request.getJobId(), userId);
        if (job == null) {
            throw notFound();
        }
        ResumeVersion version = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, request.getResumeVersionId())
                .eq(ResumeVersion::getUserId, userId));
        if (version == null) {
            throw notFound();
        }
        if (!ResumeVersionStatus.FINALIZED.equals(version.getStatus())) {
            throw new InvalidResourceStateException("创建投递只能绑定已定稿的简历版本");
        }
        long ongoingCount = applicationMapper.selectCount(new LambdaQueryWrapper<Application>()
                .eq(Application::getUserId, userId)
                .eq(Application::getJobId, job.getId())
                .ne(Application::getCurrentStage, ApplicationStage.ENDED));
        if (ongoingCount > 0) {
            throw duplicateOngoing();
        }

        Company company = companyMapper.selectOne(new LambdaQueryWrapper<Company>()
                .eq(Company::getId, job.getCompanyId())
                .eq(Company::getUserId, userId));
        if (company == null) {
            throw notFound();
        }

        LocalDateTime now = LocalDateTime.now();
        Application application = new Application();
        application.setUserId(userId);
        application.setJobId(job.getId());
        application.setResumeVersionId(version.getId());
        application.setCurrentStage(ApplicationStage.APPLIED);
        application.setJobTitleSnapshot(job.getTitle());
        application.setCompanyNameSnapshot(company.getName());
        application.setLocationSnapshot(job.getCity());
        application.setJobDescriptionSnapshot(job.getRawJd());
        application.setAppliedAt(now);
        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException exception) {
            throw duplicateOngoing();
        }
        appendHistory(application, null, ApplicationStage.APPLIED, null, "创建投递", now);
        return requireOwnedApplication(application.getId(), userId);
    }

    public List<Application> listApplications(Long userId, ApplicationStage currentStage, Long jobId) {
        LambdaQueryWrapper<Application> query = new LambdaQueryWrapper<Application>()
                .eq(Application::getUserId, userId);
        if (currentStage != null) {
            query.eq(Application::getCurrentStage, currentStage);
        }
        if (jobId != null) {
            query.eq(Application::getJobId, jobId);
        }
        return applicationMapper.selectList(query.orderByDesc(Application::getUpdatedAt)
                .orderByDesc(Application::getId));
    }

    public Application getApplication(Long applicationId, Long userId) {
        return requireOwnedApplication(applicationId, userId);
    }

    @Transactional
    public Application transition(Long applicationId, Long userId, ApplicationTransitionRequest request) {
        Application application = lockOwnedApplication(applicationId, userId);
        ApplicationEndReason reason = request.getEndReason();
        if (ApplicationStage.ENDED.equals(request.getTargetStage())) {
            if (reason == null) {
                throw new InvalidRequestException("结束投递时必须提供结束原因");
            }
            if (reason == ApplicationEndReason.OFFER_ACCEPTED || reason == ApplicationEndReason.OFFER_REJECTED) {
                throw new InvalidRequestException("Offer 结果只能通过 Offer 更新接口记录");
            }
        } else if (reason != null) {
            throw new InvalidRequestException("只有结束投递时才能提供结束原因");
        }
        moveStage(application, request.getTargetStage(), reason, request.getNote(), LocalDateTime.now());
        return requireOwnedApplication(applicationId, userId);
    }

    public List<ApplicationStageHistory> listHistory(Long applicationId, Long userId) {
        requireOwnedApplication(applicationId, userId);
        return historyMapper.selectList(new LambdaQueryWrapper<ApplicationStageHistory>()
                .eq(ApplicationStageHistory::getApplicationId, applicationId)
                .eq(ApplicationStageHistory::getUserId, userId)
                .orderByAsc(ApplicationStageHistory::getChangedAt)
                .orderByAsc(ApplicationStageHistory::getId));
    }

    @Transactional
    public Assessment createAssessment(Long applicationId, Long userId, AssessmentRequest request) {
        requireOwnedApplication(applicationId, userId);
        Assessment assessment = new Assessment();
        assessment.setUserId(userId);
        assessment.setApplicationId(applicationId);
        apply(assessment, request);
        assessmentMapper.insert(assessment);
        return requireOwnedAssessment(applicationId, assessment.getId(), userId);
    }

    public List<Assessment> listAssessments(Long applicationId, Long userId) {
        requireOwnedApplication(applicationId, userId);
        return assessmentMapper.selectList(new LambdaQueryWrapper<Assessment>()
                .eq(Assessment::getApplicationId, applicationId)
                .eq(Assessment::getUserId, userId)
                .orderByAsc(Assessment::getScheduledAt)
                .orderByAsc(Assessment::getId));
    }

    public Assessment getAssessment(Long applicationId, Long assessmentId, Long userId) {
        requireOwnedApplication(applicationId, userId);
        return requireOwnedAssessment(applicationId, assessmentId, userId);
    }

    @Transactional
    public Assessment updateAssessment(Long applicationId, Long assessmentId, Long userId,
                                       AssessmentRequest request) {
        requireOwnedApplication(applicationId, userId);
        requireOwnedAssessment(applicationId, assessmentId, userId);
        updateOrNotFound(assessmentMapper.update(null, new LambdaUpdateWrapper<Assessment>()
                .set(Assessment::getType, request.getType())
                .set(Assessment::getTitle, request.getTitle())
                .set(Assessment::getScheduledAt, request.getScheduledAt())
                .set(Assessment::getOccurredAt, request.getOccurredAt())
                .set(Assessment::getResult, request.getResult())
                .set(Assessment::getNotes, request.getNotes())
                .eq(Assessment::getId, assessmentId)
                .eq(Assessment::getApplicationId, applicationId)
                .eq(Assessment::getUserId, userId)));
        return requireOwnedAssessment(applicationId, assessmentId, userId);
    }

    @Transactional
    public void deleteAssessment(Long applicationId, Long assessmentId, Long userId) {
        requireOwnedApplication(applicationId, userId);
        deleteOrNotFound(assessmentMapper.delete(new LambdaQueryWrapper<Assessment>()
                .eq(Assessment::getId, assessmentId)
                .eq(Assessment::getApplicationId, applicationId)
                .eq(Assessment::getUserId, userId)));
    }

    @Transactional
    public Interview createInterview(Long applicationId, Long userId, InterviewRequest request) {
        requireOwnedApplication(applicationId, userId);
        Interview interview = new Interview();
        interview.setUserId(userId);
        interview.setApplicationId(applicationId);
        apply(interview, request);
        interviewMapper.insert(interview);
        return requireOwnedInterview(applicationId, interview.getId(), userId);
    }

    public List<Interview> listInterviews(Long applicationId, Long userId) {
        requireOwnedApplication(applicationId, userId);
        return interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getApplicationId, applicationId)
                .eq(Interview::getUserId, userId)
                .orderByAsc(Interview::getRoundNo)
                .orderByAsc(Interview::getId));
    }

    public Interview getInterview(Long applicationId, Long interviewId, Long userId) {
        requireOwnedApplication(applicationId, userId);
        return requireOwnedInterview(applicationId, interviewId, userId);
    }

    @Transactional
    public Interview updateInterview(Long applicationId, Long interviewId, Long userId,
                                     InterviewRequest request) {
        requireOwnedApplication(applicationId, userId);
        requireOwnedInterview(applicationId, interviewId, userId);
        updateOrNotFound(interviewMapper.update(null, new LambdaUpdateWrapper<Interview>()
                .set(Interview::getRoundNo, request.getRoundNo())
                .set(Interview::getType, request.getType())
                .set(Interview::getTitle, request.getTitle())
                .set(Interview::getScheduledAt, request.getScheduledAt())
                .set(Interview::getOccurredAt, request.getOccurredAt())
                .set(Interview::getResult, request.getResult())
                .set(Interview::getNotes, request.getNotes())
                .eq(Interview::getId, interviewId)
                .eq(Interview::getApplicationId, applicationId)
                .eq(Interview::getUserId, userId)));
        return requireOwnedInterview(applicationId, interviewId, userId);
    }

    @Transactional
    public void deleteInterview(Long applicationId, Long interviewId, Long userId) {
        requireOwnedApplication(applicationId, userId);
        deleteOrNotFound(interviewMapper.delete(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getId, interviewId)
                .eq(Interview::getApplicationId, applicationId)
                .eq(Interview::getUserId, userId)));
    }

    @Transactional
    public Offer createOffer(Long applicationId, Long userId, OfferCreateRequest request) {
        Application application = lockOwnedApplication(applicationId, userId);
        if (ApplicationStage.ENDED.equals(application.getCurrentStage())) {
            throw new InvalidResourceStateException("已结束的投递不能创建 Offer");
        }
        if (offerMapper.selectCount(new LambdaQueryWrapper<Offer>()
                .eq(Offer::getApplicationId, applicationId)) > 0) {
            throw new DuplicateResourceException("同一投递只能有一个 Offer");
        }

        Offer offer = new Offer();
        offer.setUserId(userId);
        offer.setApplicationId(applicationId);
        offer.setStatus(OfferStatus.CONSIDERING);
        apply(offer, request);
        try {
            offerMapper.insert(offer);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateResourceException("同一投递只能有一个 Offer");
        }
        if (!ApplicationStage.OFFER.equals(application.getCurrentStage())) {
            moveStage(application, ApplicationStage.OFFER, null, "收到 Offer", LocalDateTime.now());
        }
        return requireOwnedOffer(applicationId, userId);
    }

    public Offer getOffer(Long applicationId, Long userId) {
        requireOwnedApplication(applicationId, userId);
        return requireOwnedOffer(applicationId, userId);
    }

    @Transactional
    public Offer updateOffer(Long applicationId, Long userId, OfferUpdateRequest request) {
        Application application = lockOwnedApplication(applicationId, userId);
        Offer offer = requireOwnedOffer(applicationId, userId);
        if (!OfferStatus.CONSIDERING.equals(offer.getStatus())) {
            throw new InvalidResourceStateException("已终结的 Offer 不能再次修改");
        }
        updateOrNotFound(offerMapper.update(null, new LambdaUpdateWrapper<Offer>()
                .set(Offer::getStatus, request.getStatus())
                .set(Offer::getPositionTitle, request.getPositionTitle())
                .set(Offer::getCompensation, request.getCompensation())
                .set(Offer::getExpiresAt, request.getExpiresAt())
                .set(Offer::getNotes, request.getNotes())
                .eq(Offer::getId, offer.getId())
                .eq(Offer::getApplicationId, applicationId)
                .eq(Offer::getUserId, userId)));

        if (OfferStatus.ACCEPTED.equals(request.getStatus())) {
            moveStage(application, ApplicationStage.ENDED, ApplicationEndReason.OFFER_ACCEPTED,
                    "接受 Offer", LocalDateTime.now());
        } else if (OfferStatus.REJECTED.equals(request.getStatus())) {
            moveStage(application, ApplicationStage.ENDED, ApplicationEndReason.OFFER_REJECTED,
                    "拒绝 Offer", LocalDateTime.now());
        }
        return requireOwnedOffer(applicationId, userId);
    }

    public FinalReview getFinalReview(Long applicationId, Long userId) {
        requireOwnedApplication(applicationId, userId);
        return requireOwnedFinalReview(applicationId, userId);
    }

    @Transactional
    public FinalReview upsertFinalReview(Long applicationId, Long userId, FinalReviewRequest request) {
        Application application = lockOwnedApplication(applicationId, userId);
        if (!ApplicationStage.ENDED.equals(application.getCurrentStage())) {
            throw new InvalidResourceStateException("投递结束后才能填写最终复盘");
        }
        FinalReview review = finalReviewMapper.selectOne(new LambdaQueryWrapper<FinalReview>()
                .eq(FinalReview::getApplicationId, applicationId)
                .eq(FinalReview::getUserId, userId));
        if (review == null) {
            review = new FinalReview();
            review.setUserId(userId);
            review.setApplicationId(applicationId);
            apply(review, request);
            try {
                finalReviewMapper.insert(review);
            } catch (DuplicateKeyException exception) {
                throw new DuplicateResourceException("同一投递只能有一份最终复盘");
            }
        } else {
            updateOrNotFound(finalReviewMapper.update(null, new LambdaUpdateWrapper<FinalReview>()
                    .set(FinalReview::getSummary, request.getSummary())
                    .set(FinalReview::getLessonsLearned, request.getLessonsLearned())
                    .set(FinalReview::getImprovements, request.getImprovements())
                    .set(FinalReview::getRating, request.getRating())
                    .set(FinalReview::getReviewedAt, request.getReviewedAt())
                    .eq(FinalReview::getId, review.getId())
                    .eq(FinalReview::getApplicationId, applicationId)
                    .eq(FinalReview::getUserId, userId)));
        }
        return requireOwnedFinalReview(applicationId, userId);
    }

    private void moveStage(Application application, ApplicationStage targetStage,
                           ApplicationEndReason endReason, String note, LocalDateTime now) {
        ApplicationStage fromStage = application.getCurrentStage();
        if (!TRANSITIONS.getOrDefault(fromStage, Set.of()).contains(targetStage)) {
            throw new InvalidResourceStateException("不允许从 " + fromStage + " 迁移到 " + targetStage);
        }
        LocalDateTime endedAt = ApplicationStage.ENDED.equals(targetStage) ? now : null;
        updateOrNotFound(applicationMapper.update(null, new LambdaUpdateWrapper<Application>()
                .set(Application::getCurrentStage, targetStage)
                .set(Application::getEndReason, endReason)
                .set(Application::getEndNote, ApplicationStage.ENDED.equals(targetStage) ? note : null)
                .set(Application::getEndedAt, endedAt)
                .eq(Application::getId, application.getId())
                .eq(Application::getUserId, application.getUserId())
                .eq(Application::getCurrentStage, fromStage)));
        appendHistory(application, fromStage, targetStage, endReason, note, now);
        application.setCurrentStage(targetStage);
        application.setEndReason(endReason);
        application.setEndNote(ApplicationStage.ENDED.equals(targetStage) ? note : null);
        application.setEndedAt(endedAt);
    }

    private void appendHistory(Application application, ApplicationStage fromStage,
                               ApplicationStage toStage, ApplicationEndReason endReason,
                               String note, LocalDateTime changedAt) {
        ApplicationStageHistory history = new ApplicationStageHistory();
        history.setUserId(application.getUserId());
        history.setApplicationId(application.getId());
        history.setFromStage(fromStage);
        history.setToStage(toStage);
        history.setEndReason(endReason);
        history.setNote(note);
        history.setChangedAt(changedAt);
        historyMapper.insert(history);
    }

    private Application requireOwnedApplication(Long applicationId, Long userId) {
        Application application = applicationMapper.selectOne(new LambdaQueryWrapper<Application>()
                .eq(Application::getId, applicationId)
                .eq(Application::getUserId, userId));
        if (application == null) {
            throw notFound();
        }
        return application;
    }

    private Application lockOwnedApplication(Long applicationId, Long userId) {
        Application application = applicationMapper.selectOwnedForUpdate(applicationId, userId);
        if (application == null) {
            throw notFound();
        }
        return application;
    }

    private Assessment requireOwnedAssessment(Long applicationId, Long assessmentId, Long userId) {
        Assessment assessment = assessmentMapper.selectOne(new LambdaQueryWrapper<Assessment>()
                .eq(Assessment::getId, assessmentId)
                .eq(Assessment::getApplicationId, applicationId)
                .eq(Assessment::getUserId, userId));
        if (assessment == null) {
            throw notFound();
        }
        return assessment;
    }

    private Interview requireOwnedInterview(Long applicationId, Long interviewId, Long userId) {
        Interview interview = interviewMapper.selectOne(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getId, interviewId)
                .eq(Interview::getApplicationId, applicationId)
                .eq(Interview::getUserId, userId));
        if (interview == null) {
            throw notFound();
        }
        return interview;
    }

    private Offer requireOwnedOffer(Long applicationId, Long userId) {
        Offer offer = offerMapper.selectOne(new LambdaQueryWrapper<Offer>()
                .eq(Offer::getApplicationId, applicationId)
                .eq(Offer::getUserId, userId));
        if (offer == null) {
            throw notFound();
        }
        return offer;
    }

    private FinalReview requireOwnedFinalReview(Long applicationId, Long userId) {
        FinalReview review = finalReviewMapper.selectOne(new LambdaQueryWrapper<FinalReview>()
                .eq(FinalReview::getApplicationId, applicationId)
                .eq(FinalReview::getUserId, userId));
        if (review == null) {
            throw notFound();
        }
        return review;
    }

    private void apply(Assessment entity, AssessmentRequest request) {
        entity.setType(request.getType());
        entity.setTitle(request.getTitle());
        entity.setScheduledAt(request.getScheduledAt());
        entity.setOccurredAt(request.getOccurredAt());
        entity.setResult(request.getResult());
        entity.setNotes(request.getNotes());
    }

    private void apply(Interview entity, InterviewRequest request) {
        entity.setRoundNo(request.getRoundNo());
        entity.setType(request.getType());
        entity.setTitle(request.getTitle());
        entity.setScheduledAt(request.getScheduledAt());
        entity.setOccurredAt(request.getOccurredAt());
        entity.setResult(request.getResult());
        entity.setNotes(request.getNotes());
    }

    private void apply(Offer entity, OfferCreateRequest request) {
        entity.setPositionTitle(request.getPositionTitle());
        entity.setCompensation(request.getCompensation());
        entity.setExpiresAt(request.getExpiresAt());
        entity.setNotes(request.getNotes());
    }

    private void apply(FinalReview entity, FinalReviewRequest request) {
        entity.setSummary(request.getSummary());
        entity.setLessonsLearned(request.getLessonsLearned());
        entity.setImprovements(request.getImprovements());
        entity.setRating(request.getRating());
        entity.setReviewedAt(request.getReviewedAt());
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

    private DuplicateResourceException duplicateOngoing() {
        return new DuplicateResourceException("同一用户同一岗位同时只能有一个进行中的投递");
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("资源不存在");
    }

    private static Map<ApplicationStage, Set<ApplicationStage>> transitions() {
        Map<ApplicationStage, Set<ApplicationStage>> matrix = new EnumMap<>(ApplicationStage.class);
        matrix.put(ApplicationStage.APPLIED, EnumSet.of(ApplicationStage.ASSESSMENT,
                ApplicationStage.INTERVIEW, ApplicationStage.OFFER, ApplicationStage.ENDED));
        matrix.put(ApplicationStage.ASSESSMENT, EnumSet.of(ApplicationStage.INTERVIEW,
                ApplicationStage.OFFER, ApplicationStage.ENDED));
        matrix.put(ApplicationStage.INTERVIEW, EnumSet.of(ApplicationStage.OFFER, ApplicationStage.ENDED));
        matrix.put(ApplicationStage.OFFER, EnumSet.of(ApplicationStage.ENDED));
        matrix.put(ApplicationStage.ENDED, EnumSet.noneOf(ApplicationStage.class));
        return Map.copyOf(matrix);
    }
}
