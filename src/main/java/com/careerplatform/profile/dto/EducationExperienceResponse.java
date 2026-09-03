package com.careerplatform.profile.dto;

import java.time.LocalDate;

public record EducationExperienceResponse(Long id, String schoolName, String major, String degree,
                                          LocalDate startDate, LocalDate endDate, String description) {
}
