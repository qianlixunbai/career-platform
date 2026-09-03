package com.careerplatform.profile.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.profile.dto.EducationExperienceRequest;
import com.careerplatform.profile.dto.EducationExperienceResponse;
import com.careerplatform.profile.entity.EducationExperience;
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
@RequestMapping("/api/v1/education-experiences")
public class EducationExperienceController {
    private final ProfileService profileService;
    public EducationExperienceController(ProfileService profileService) { this.profileService = profileService; }
    @PostMapping public ResponseEntity<EducationExperienceResponse> create(@CurrentUserId Long userId, @Valid @RequestBody EducationExperienceRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(profileService.createEducation(userId, request))); }
    @GetMapping public List<EducationExperienceResponse> list(@CurrentUserId Long userId) { return profileService.listEducation(userId).stream().map(this::toResponse).toList(); }
    @GetMapping("/{id}") public EducationExperienceResponse get(@PathVariable Long id, @CurrentUserId Long userId) { return toResponse(profileService.getEducation(id, userId)); }
    @PutMapping("/{id}") public EducationExperienceResponse update(@PathVariable Long id, @CurrentUserId Long userId, @Valid @RequestBody EducationExperienceRequest request) { return toResponse(profileService.updateEducation(id, userId, request)); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id, @CurrentUserId Long userId) { profileService.deleteEducation(id, userId); return ResponseEntity.noContent().build(); }
    private EducationExperienceResponse toResponse(EducationExperience value) { return new EducationExperienceResponse(value.getId(), value.getSchoolName(), value.getMajor(), value.getDegree(), value.getStartDate(), value.getEndDate(), value.getDescription()); }
}
