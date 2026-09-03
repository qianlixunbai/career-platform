package com.careerplatform.profile.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.profile.dto.SkillCreateRequest;
import com.careerplatform.profile.dto.SkillResponse;
import com.careerplatform.profile.dto.UserSkillCreateRequest;
import com.careerplatform.profile.dto.UserSkillResponse;
import com.careerplatform.profile.dto.UserSkillUpdateRequest;
import com.careerplatform.profile.entity.Skill;
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
public class SkillController {
    private final ProfileService profileService;
    public SkillController(ProfileService profileService) { this.profileService = profileService; }
    @PostMapping("/skills") public ResponseEntity<SkillResponse> createSkill(@CurrentUserId Long ignoredUserId, @Valid @RequestBody SkillCreateRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(profileService.createSkill(request.getName()))); }
    @GetMapping("/skills") public List<SkillResponse> listSkills(@CurrentUserId Long ignoredUserId) { return profileService.listSkills().stream().map(this::toResponse).toList(); }
    @GetMapping("/skills/{id}") public SkillResponse getSkill(@PathVariable Long id, @CurrentUserId Long ignoredUserId) { return toResponse(profileService.getSkill(id)); }
    @PostMapping("/user-skills") public ResponseEntity<UserSkillResponse> createUserSkill(@CurrentUserId Long userId, @Valid @RequestBody UserSkillCreateRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(profileService.createUserSkill(userId, request.getSkillId(), request.getProficiency()))); }
    @GetMapping("/user-skills") public List<UserSkillResponse> listUserSkills(@CurrentUserId Long userId) { return profileService.listUserSkills(userId).stream().map(this::toResponse).toList(); }
    @GetMapping("/user-skills/{id}") public UserSkillResponse getUserSkill(@PathVariable Long id, @CurrentUserId Long userId) { return toResponse(profileService.getUserSkill(id, userId)); }
    @PutMapping("/user-skills/{id}") public UserSkillResponse updateUserSkill(@PathVariable Long id, @CurrentUserId Long userId, @Valid @RequestBody UserSkillUpdateRequest request) { return toResponse(profileService.updateUserSkill(id, userId, request.getProficiency())); }
    @DeleteMapping("/user-skills/{id}") public ResponseEntity<Void> deleteUserSkill(@PathVariable Long id, @CurrentUserId Long userId) { profileService.deleteUserSkill(id, userId); return ResponseEntity.noContent().build(); }
    private SkillResponse toResponse(Skill skill) { return new SkillResponse(skill.getId(), skill.getName()); }
    private UserSkillResponse toResponse(ProfileService.UserSkillDetail detail) { return new UserSkillResponse(detail.userSkill().getId(), detail.userSkill().getSkillId(), detail.skill().getName(), detail.userSkill().getProficiency()); }
}
