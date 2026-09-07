package com.careerplatform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerplatform.ai.client.*;
import com.careerplatform.ai.dto.rag.*;
import com.careerplatform.ai.exception.*;
import com.careerplatform.common.exception.*;
import com.careerplatform.learning.entity.LearningMaterialChunk;
import com.careerplatform.learning.mapper.LearningMaterialChunkMapper;
import com.careerplatform.learning.service.LearningService;
import org.springframework.stereotype.Service;
import java.util.*;

/** Course-scale owner/plan-scoped retrieval. No write mapper operation or tool invocation. */
@Service
public class RagService {
    public static final int MAX_CANDIDATES = 1000;
    public static final int TOP_K = 4;
    public static final int MAX_CONTEXT = 4000;
    public static final double MIN_SIMILARITY = 0.55;
    public static final String NO_EVIDENCE = "当前学习资料中没有找到足够依据。";
    private final LearningService learning;
    private final LearningMaterialChunkMapper chunks;
    private final EmbeddingGateway embedding;
    private final RagChatGateway chat;
    private final RagPromptFactory prompts;

    public RagService(LearningService learning, LearningMaterialChunkMapper chunks, EmbeddingGateway embedding,
                      RagChatGateway chat, RagPromptFactory prompts) {
        this.learning = learning; this.chunks = chunks; this.embedding = embedding; this.chat = chat; this.prompts = prompts;
    }
    public boolean available(Long planId, Long userId) {
        learning.getPlan(planId, userId);
        return embedding.isAvailable() && chat.isAvailable();
    }
    public RagAnswer query(Long planId, Long userId, RagQueryRequest request) {
        learning.getPlan(planId, userId);
        if (request == null || request.question() == null || request.question().isBlank() || request.question().length() > 1000)
            throw new InvalidRequestException("问题不能为空且不能超过1000个字符");
        if (!embedding.isAvailable() || !chat.isAvailable()) throw new AiServiceUnavailableException("学习资料问答当前未配置");
        String identity = embedding.identity();
        var candidates = chunks.selectCandidates(userId, planId, identity);
        if (candidates.size() > MAX_CANDIDATES) throw new InvalidResourceStateException("本计划索引片段超过检索上限，请减少资料数量");
        if (candidates.isEmpty()) return insufficient();
        var queryVectors = embedding.embed(List.of(request.question()));
        if (queryVectors == null || queryVectors.size() != 1) throw new AiProviderException("问题向量无法安全处理");
        float[] queryVector = queryVectors.getFirst();
        EmbeddingVectors.validate(queryVector);
        List<Scored> ranked = new ArrayList<>();
        for (var chunk : candidates) {
            // SQL is the first boundary; defensive validation also rejects incorrectly returned rows.
            if (!userId.equals(chunk.getUserId()) || !identity.equals(chunk.getEmbeddingIdentity()))
                throw new ResourceNotFoundException("学习资料不存在");
            double score = EmbeddingVectors.cosine(queryVector, EmbeddingVectors.decode(chunk.getEmbedding()));
            if (score >= MIN_SIMILARITY) ranked.add(new Scored(chunk, score));
        }
        ranked.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s -> s.chunk().getId()));
        Map<String, RagCitation> trusted = new LinkedHashMap<>();
        int chars = 0;
        for (var scored : ranked) {
            var chunk = scored.chunk();
            if (trusted.size() == TOP_K) break;
            if (chunk.getText() == null || chunk.getText().isBlank() || chunk.getText().length() > 1000)
                throw new InvalidResourceStateException("学习资料索引无效，请重新索引");
            if (chars + chunk.getText().length() > MAX_CONTEXT) continue;
            String key = "c" + (trusted.size() + 1);
            trusted.put(key, source(planId, chunk.getMaterialId(), chunk.getId(), userId, key));
            chars += chunk.getText().length();
        }
        if (trusted.isEmpty()) return insufficient();
        var result = chat.answer(prompts.systemInstruction(), prompts.content(request.question(), List.copyOf(trusted.values())));
        if (result == null || result.answer() == null || result.answer().isBlank() || result.answer().length() > 4000
                || result.evidenceInsufficient() == null || result.citationKeys() == null || result.citationKeys().size() > TOP_K)
            throw invalid();
        Set<String> keys = new LinkedHashSet<>(result.citationKeys());
        if (keys.size() != result.citationKeys().size() || keys.stream().anyMatch(k -> k == null || !trusted.containsKey(k))) throw invalid();
        if (result.evidenceInsufficient()) {
            if (!keys.isEmpty()) throw invalid();
            return insufficient();
        }
        if (keys.isEmpty()) throw invalid();
        List<RagCitation> citations = new ArrayList<>();
        for (String key : keys) {
            var before = trusted.get(key);
            // Recheck owner and chunk existence after network I/O; deleted/reindexed evidence is not served stale.
            var current = source(planId, before.materialId(), before.chunkId(), userId, key);
            if (!current.originalExcerpt().equals(before.originalExcerpt())) throw new InvalidResourceStateException("资料已更新，请重新提问");
            citations.add(current);
        }
        return new RagAnswer(result.answer(), List.copyOf(citations), List.of(), false);
    }
    public RagCitation source(Long planId, Long materialId, Long chunkId, Long userId, String key) {
        var material = learning.getMaterial(planId, materialId, userId);
        var chunk = chunks.selectOne(new LambdaQueryWrapper<LearningMaterialChunk>()
                .eq(LearningMaterialChunk::getId, chunkId).eq(LearningMaterialChunk::getMaterialId, materialId)
                .eq(LearningMaterialChunk::getUserId, userId));
        if (chunk == null) throw new ResourceNotFoundException("学习资料片段不存在");
        if (!"READY".equals(material.getIndexStatus())) throw new InvalidResourceStateException("学习资料尚未完成索引");
        return new RagCitation(key, materialId, material.getTitle(), chunkId, chunk.getLocationLabel(),
                chunk.getPageNumber(), chunk.getText());
    }
    private static RagAnswer insufficient() { return new RagAnswer(NO_EVIDENCE, List.of(), List.of(), true); }
    private static AiInvalidResponseException invalid() { return new AiInvalidResponseException("AI 返回内容无法安全处理"); }
    private record Scored(LearningMaterialChunk chunk, double score) { }
}
