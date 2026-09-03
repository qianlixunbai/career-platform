package com.careerplatform.profile.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.profile.dto.CertificateAwardRequest;
import com.careerplatform.profile.dto.CertificateAwardResponse;
import com.careerplatform.profile.dto.InternshipExperienceRequest;
import com.careerplatform.profile.dto.InternshipExperienceResponse;
import com.careerplatform.profile.dto.ProjectExperienceRequest;
import com.careerplatform.profile.dto.ProjectExperienceResponse;
import com.careerplatform.profile.entity.CertificateAward;
import com.careerplatform.profile.entity.InternshipExperience;
import com.careerplatform.profile.entity.ProjectExperience;
import com.careerplatform.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ExperienceController {
    private final ProfileService profileService;
    public ExperienceController(ProfileService profileService) { this.profileService = profileService; }

    @PostMapping("/project-experiences") public ResponseEntity<ProjectExperienceResponse> createProject(@CurrentUserId Long userId, @Valid @RequestBody ProjectExperienceRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(profileService.createProject(userId, request))); }
    @GetMapping("/project-experiences") public List<ProjectExperienceResponse> listProjects(@CurrentUserId Long userId) { return profileService.listProjects(userId).stream().map(this::toResponse).toList(); }
    @GetMapping("/project-experiences/{id}") public ProjectExperienceResponse getProject(@PathVariable Long id, @CurrentUserId Long userId) { return toResponse(profileService.getProject(id, userId)); }
    @PutMapping("/project-experiences/{id}") public ProjectExperienceResponse updateProject(@PathVariable Long id, @CurrentUserId Long userId, @Valid @RequestBody ProjectExperienceRequest request) { return toResponse(profileService.updateProject(id, userId, request)); }
    @DeleteMapping("/project-experiences/{id}") public ResponseEntity<Void> deleteProject(@PathVariable Long id, @CurrentUserId Long userId) { profileService.deleteProject(id, userId); return ResponseEntity.noContent().build(); }

    @PostMapping("/internship-experiences") public ResponseEntity<InternshipExperienceResponse> createInternship(@CurrentUserId Long userId, @Valid @RequestBody InternshipExperienceRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(profileService.createInternship(userId, request))); }
    @GetMapping("/internship-experiences") public List<InternshipExperienceResponse> listInternships(@CurrentUserId Long userId) { return profileService.listInternships(userId).stream().map(this::toResponse).toList(); }
    @GetMapping("/internship-experiences/{id}") public InternshipExperienceResponse getInternship(@PathVariable Long id, @CurrentUserId Long userId) { return toResponse(profileService.getInternship(id, userId)); }
    @PutMapping("/internship-experiences/{id}") public InternshipExperienceResponse updateInternship(@PathVariable Long id, @CurrentUserId Long userId, @Valid @RequestBody InternshipExperienceRequest request) { return toResponse(profileService.updateInternship(id, userId, request)); }
    @DeleteMapping("/internship-experiences/{id}") public ResponseEntity<Void> deleteInternship(@PathVariable Long id, @CurrentUserId Long userId) { profileService.deleteInternship(id, userId); return ResponseEntity.noContent().build(); }

    @PostMapping("/certificate-awards") public ResponseEntity<CertificateAwardResponse> createCertificate(@CurrentUserId Long userId, @Valid @RequestBody CertificateAwardRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(profileService.createCertificate(userId, request))); }
    @GetMapping("/certificate-awards") public List<CertificateAwardResponse> listCertificates(@CurrentUserId Long userId) { return profileService.listCertificates(userId).stream().map(this::toResponse).toList(); }
    @GetMapping("/certificate-awards/{id}") public CertificateAwardResponse getCertificate(@PathVariable Long id, @CurrentUserId Long userId) { return toResponse(profileService.getCertificate(id, userId)); }
    @PutMapping("/certificate-awards/{id}") public CertificateAwardResponse updateCertificate(@PathVariable Long id, @CurrentUserId Long userId, @Valid @RequestBody CertificateAwardRequest request) { return toResponse(profileService.updateCertificate(id, userId, request)); }
    @DeleteMapping("/certificate-awards/{id}") public ResponseEntity<Void> deleteCertificate(@PathVariable Long id, @CurrentUserId Long userId) { profileService.deleteCertificate(id, userId); return ResponseEntity.noContent().build(); }

    private ProjectExperienceResponse toResponse(ProjectExperience value) { return new ProjectExperienceResponse(value.getId(), value.getProjectName(), value.getRole(), value.getStartDate(), value.getEndDate(), value.getDescription(), value.getTechStack(), value.getProjectUrl()); }
    private InternshipExperienceResponse toResponse(InternshipExperience value) { return new InternshipExperienceResponse(value.getId(), value.getCompanyName(), value.getPosition(), value.getStartDate(), value.getEndDate(), value.getDescription()); }
    private CertificateAwardResponse toResponse(CertificateAward value) { return new CertificateAwardResponse(value.getId(), value.getName(), value.getType(), value.getIssuer(), value.getIssueDate(), value.getDescription()); }
}
