package com.careerplatform.ai.dto;

import com.careerplatform.career.dto.JobRequirementResponse;
import java.util.List;

public record JdParseConfirmResponse(int createdCount, List<JobRequirementResponse> createdRequirements) {
}
