package com.careerplatform.learning.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.careerplatform.learning.entity.LearningMaterial;
import com.careerplatform.learning.mapper.LearningMaterialMapper;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningMaterialMapperTest {
    @Test void candidateSqlAppliesOwnerPlanModelAndHardRowLimit() {
        var configuration=new MybatisConfiguration();
        new MapperAnnotationBuilder(configuration,com.careerplatform.learning.mapper.LearningMaterialChunkMapper.class).parse();
        String sql=configuration.getMappedStatement(com.careerplatform.learning.mapper.LearningMaterialChunkMapper.class.getName()+".selectCandidates")
                .getBoundSql(java.util.Map.of("userId",1L,"planId",2L,"identity","fixture")).getSql();
        assertThat(sql).contains("c.user_id = ?", "m.plan_id = ?", "m.index_status = 'READY'",
                "c.embedding_identity = ?", "m.embedding_identity = ?", "LIMIT 1000");
    }
    @Test void blobSelectIsSingleRowAndDefaultMethodUnwrapsBytes() throws Exception {
        var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        new MapperAnnotationBuilder(configuration,LearningMaterialMapper.class).parse();
        var method=LearningMaterialMapper.class.getMethod("selectOwnedFileRow",Long.class,Long.class,Long.class);
        assertThat(new MapperMethod.MethodSignature(configuration,LearningMaterialMapper.class,method).returnsMany()).isFalse();
        var statement=configuration.getMappedStatement(LearningMaterialMapper.class.getName()+".selectOwnedFileRow");
        assertThat(statement.getResultMaps().getFirst().getType()).isEqualTo(LearningMaterial.class);
        var mapper=mock(LearningMaterialMapper.class,CALLS_REAL_METHODS);
        var row=new LearningMaterial(); row.setFileContent(new byte[]{1,2,3});
        when(mapper.selectOwnedFileRow(1L,2L,3L)).thenReturn(row);
        assertThat(mapper.selectOwnedFileContent(1L,2L,3L)).containsExactly((byte)1,(byte)2,(byte)3);
        assertThat(mapper.selectOwnedFileContent(99L,2L,3L)).isNull();
    }
}
