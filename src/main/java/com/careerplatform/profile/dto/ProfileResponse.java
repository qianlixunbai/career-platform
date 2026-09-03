package com.careerplatform.profile.dto;

public record ProfileResponse(Long id, String fullName, String phone, String avatarUrl, String currentCity,
                              String personalWebsite, String githubUrl) {
}
