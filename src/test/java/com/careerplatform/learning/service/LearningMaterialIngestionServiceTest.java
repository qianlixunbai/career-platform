package com.careerplatform.learning.service;

import com.careerplatform.ai.client.EmbeddingGateway;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.careerplatform.learning.entity.LearningMaterial;
import com.careerplatform.learning.entity.LearningMaterialChunk;
import com.careerplatform.learning.entity.LearningPlan;
import com.careerplatform.learning.mapper.LearningMaterialChunkMapper;
import com.careerplatform.learning.mapper.LearningMaterialMapper;
import com.careerplatform.learning.mapper.LearningPlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningMaterialIngestionServiceTest {

    @Mock private LearningService learningService;
    @Mock private LearningMaterialMapper materialMapper;
    @Mock private LearningMaterialChunkMapper chunkMapper;
    @Mock private LearningPlanMapper planMapper;
    @Mock private MaterialDocumentParser parser;
    @Mock private EmbeddingGateway embeddingGateway;
    @Mock private TransactionTemplate transactionTemplate;

    private LearningMaterialIngestionService service;
    private LearningMaterial material;
    private byte[] bytes;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "ingestion-test"),
                LearningMaterial.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "ingestion-test"),
                LearningMaterialChunk.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "ingestion-test"),
                LearningPlan.class);
        service = new LearningMaterialIngestionService(learningService, materialMapper, chunkMapper,
                planMapper, parser, embeddingGateway, transactionTemplate);
        material = new LearningMaterial();
        material.setId(41L);
        material.setPlanId(7L);
        material.setUserId(9L);
        bytes = "small document".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(learningService.getPlan(7L, 9L)).thenReturn(new LearningPlan());
        when(learningService.createMaterial(eq(7L), eq(9L), any())).thenReturn(material);
        when(parser.parse(eq(bytes), eq("lesson.docx"), eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .thenReturn(List.of(new MaterialDocumentParser.ParsedChunk(0, "small document", "Paragraph 1", null)));
        when(materialMapper.countUploadedInPlan(7L, 9L)).thenReturn(0L);
        when(materialMapper.sumUploadedBytesInPlan(7L, 9L)).thenReturn(0L);
        when(materialMapper.update(any(), any())).thenReturn(1);
        when(planMapper.selectOne(any())).thenReturn(new LearningPlan());

        doAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());
    }

    @Test
    void persistsOriginalAndReplacesChunksInsideIndexBoundary() {
        when(learningService.getMaterial(7L, 41L, 9L)).thenReturn(material);
        when(materialMapper.selectOne(any())).thenReturn(material);
        when(materialMapper.selectOwnedFileContent(41L, 7L, 9L)).thenReturn(bytes);
        when(chunkMapper.countReadyChunksExcluding(7L, 9L, 41L)).thenReturn(0L);
        when(embeddingGateway.isAvailable()).thenReturn(true);
        when(embeddingGateway.identity()).thenReturn("fake-v1");
        when(embeddingGateway.embed(any())).thenReturn(List.of(new float[]{1.0f, 0.0f}));
        when(chunkMapper.insert(any(LearningMaterialChunk.class))).thenReturn(1);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback =
                    (java.util.function.Consumer<org.springframework.transaction.TransactionStatus>) invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        MockMultipartFile file = new MockMultipartFile("file", "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);

        service.upload(7L, 9L, file, null);

        verify(learningService).createMaterial(eq(7L), eq(9L), any());
        verify(embeddingGateway).embed(List.of("small document"));
        verify(chunkMapper).delete(any());
        verify(chunkMapper).insert(any(LearningMaterialChunk.class));
        verify(materialMapper, org.mockito.Mockito.times(2)).update(any(), any());
    }

    @Test
    void unavailableEmbeddingLeavesUploadedMaterialFailedAndUsesSafeException() {
        when(embeddingGateway.isAvailable()).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile("file", "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);

        assertThatThrownBy(() -> service.upload(7L, 9L, file, null))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("学习资料向量服务当前未配置");

        verify(materialMapper, org.mockito.Mockito.times(2)).update(any(), any());
    }
}
