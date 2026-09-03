package com.careerplatform.profile.dto;

import java.time.LocalDate;

public record ProjectExperienceResponse(Long id, String projectName, String role, LocalDate startDate,
                                        LocalDate endDate, String description, String techStack, String projectUrl) {
}
