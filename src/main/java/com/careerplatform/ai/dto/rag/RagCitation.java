package com.careerplatform.ai.dto.rag;

public record RagCitation(String citationKey, Long materialId, String materialName, Long chunkId,
                          String locationLabel, Integer pageNumber, String originalExcerpt) { }
