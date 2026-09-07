package com.careerplatform.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careerplatform.learning.entity.LearningMaterial;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LearningMaterialMapper extends BaseMapper<LearningMaterial> {

    @Select("SELECT COUNT(*) FROM learning_material "
            + "WHERE plan_id = #{planId} AND user_id = #{userId} AND file_size IS NOT NULL")
    long countUploadedInPlan(@Param("planId") Long planId, @Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(file_size), 0) FROM learning_material "
            + "WHERE plan_id = #{planId} AND user_id = #{userId} AND file_size IS NOT NULL")
    long sumUploadedBytesInPlan(@Param("planId") Long planId, @Param("userId") Long userId);

    @Select("SELECT file_content FROM learning_material "
            + "WHERE id = #{materialId} AND plan_id = #{planId} AND user_id = #{userId}")
    LearningMaterial selectOwnedFileRow(@Param("materialId") Long materialId,
                                  @Param("planId") Long planId,
                                  @Param("userId") Long userId);

    /** MyBatis treats array method return types as multiple rows, so unwrap a single row instead. */
    default byte[] selectOwnedFileContent(Long materialId, Long planId, Long userId) {
        LearningMaterial row = selectOwnedFileRow(materialId, planId, userId);
        return row == null ? null : row.getFileContent();
    }
}
