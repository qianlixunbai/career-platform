package com.careerplatform.profile.dto;

public record ProfileResponse(Long id, String fullName, String phone, String email, String currentCity,
                              String personalWebsite, String githubUrl) {
}
