package com.careerplatform.ai.dto.rag;

import java.util.List;

public record RagAnswer(String answer, List<RagCitation> citations, List<String> warnings,
                        boolean evidenceInsufficient) { }
