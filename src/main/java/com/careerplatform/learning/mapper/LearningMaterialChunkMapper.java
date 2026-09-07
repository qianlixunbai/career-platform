package com.careerplatform.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careerplatform.learning.entity.LearningMaterialChunk;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface LearningMaterialChunkMapper extends BaseMapper<LearningMaterialChunk> {

    /**
     * Bounded owner-scoped candidate lookup. The parent material status and
     * embedding identity are part of the SQL boundary so callers cannot
     * accidentally retrieve stale or another user's chunks.
     */
    @Select("SELECT c.id, c.user_id, c.material_id, c.chunk_index, c.text, "
            + "c.location_label, c.page_number, c.embedding, c.embedding_identity, c.created_at "
            + "FROM learning_material_chunk c "
            + "JOIN learning_material m ON m.id = c.material_id AND m.user_id = c.user_id "
            + "WHERE c.user_id = #{userId} AND m.plan_id = #{planId} "
            + "AND m.index_status = 'READY' AND m.embedding_identity = #{identity} "
            + "AND c.embedding_identity = #{identity} "
            + "ORDER BY c.material_id ASC, c.chunk_index ASC LIMIT 1000")
    List<LearningMaterialChunk> selectCandidates(@Param("userId") Long userId,
                                                  @Param("planId") Long planId,
                                                  @Param("identity") String identity);

    @Select("SELECT COALESCE(SUM(m.chunk_count), 0) FROM learning_material m "
            + "WHERE m.plan_id = #{planId} AND m.user_id = #{userId} "
            + "AND m.index_status = 'READY' AND m.id <> #{materialId}")
    long countReadyChunksExcluding(@Param("planId") Long planId,
                                   @Param("userId") Long userId,
                                   @Param("materialId") Long materialId);
}
