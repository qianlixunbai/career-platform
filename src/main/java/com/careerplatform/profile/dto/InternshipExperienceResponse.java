package com.careerplatform.profile.dto;

import java.time.LocalDate;

public record InternshipExperienceResponse(Long id, String companyName, String position, LocalDate startDate,
                                           LocalDate endDate, String description) {
}
