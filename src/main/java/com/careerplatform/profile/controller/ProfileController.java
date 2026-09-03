package com.careerplatform.profile.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.profile.dto.ProfileRequest;
import com.careerplatform.profile.dto.ProfileResponse;
import com.careerplatform.profile.entity.UserProfile;
import com.careerplatform.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profileService;
    public ProfileController(ProfileService profileService) { this.profileService = profileService; }

    @GetMapping
    public ProfileResponse get(@CurrentUserId Long userId) { return toResponse(profileService.getProfile(userId)); }

    @PutMapping
    public ResponseEntity<ProfileResponse> upsert(@CurrentUserId Long userId, @Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(toResponse(profileService.upsertProfile(userId, request)));
    }

    private ProfileResponse toResponse(UserProfile profile) {
        return new ProfileResponse(profile.getId(), profile.getFullName(), profile.getPhone(), profile.getAvatarUrl(),
                profile.getCurrentCity(), profile.getPersonalWebsite(), profile.getGithubUrl());
    }
}
