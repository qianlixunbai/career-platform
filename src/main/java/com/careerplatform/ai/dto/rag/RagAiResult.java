package com.careerplatform.ai.dto.rag;

import java.util.List;

/** Model owns prose and key selection only, never source metadata. */
public record RagAiResult(String answer, List<String> citationKeys, Boolean evidenceInsufficient) { }
