package com.careerplatform.ai.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagQueryRequest(@NotBlank @Size(max = 1000) String question) { }
