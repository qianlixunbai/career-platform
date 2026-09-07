package com.careerplatform.learning;

import com.careerplatform.ai.client.*;
import com.careerplatform.ai.dto.rag.*;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.service.RagService;
import com.careerplatform.auth.JwtTokenService;
import com.careerplatform.learning.dto.LearningPlanRequest;
import com.careerplatform.learning.entity.LearningMaterial;
import com.careerplatform.learning.enums.LearningPlanStatus;
import com.careerplatform.learning.mapper.LearningMaterialChunkMapper;
import com.careerplatform.learning.service.*;
import com.careerplatform.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL only. No outer test transaction; test owners are committed and explicitly cleaned. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RagMaterialIntegrationTests.InjectionConfig.class)
class RagMaterialIntegrationTests {
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired JwtTokenService tokens;
    @Autowired LearningService learning;
    @Autowired LearningMaterialIngestionService ingestion;
    @Autowired LearningMaterialChunkMapper chunks;
    @Autowired RagService rag;
    @Autowired InsertFailure injector;
    @MockitoBean EmbeddingGateway embedding;
    @MockitoBean RagChatGateway chat;
    final List<Long> owners = new ArrayList<>();

    @BeforeEach void fakeProviders() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        when(embedding.isAvailable()).thenReturn(true); when(embedding.identity()).thenReturn("mysql-fixture-v1");
        when(embedding.embed(anyList())).thenAnswer(i -> ((List<String>) i.getArgument(0)).stream()
                .map(t -> t.contains("SQL") ? new float[]{0,1} : new float[]{1,0}).toList());
        when(chat.isAvailable()).thenReturn(true);
        when(chat.answer(anyString(),anyString())).thenReturn(new RagAiResult("Java 21",List.of("c1"),false));
    }
    @AfterEach void cleanup() {
        injector.disarm();
        for (Long owner : owners) {
            for (var plan : learning.listPlans(owner)) learning.deletePlan(plan.getId(),owner);
            jdbc.update("DELETE FROM app_user WHERE id=?",owner);
        }
    }
    Session owner() {
        var user = users.register("m7_it_"+UUID.randomUUID().toString().replace("-",""),"synthetic-test-password");
        owners.add(user.getId());
        var request = new LearningPlanRequest(); request.setWeekStart(LocalDate.of(2033,1,3));
        request.setWeekEnd(LocalDate.of(2033,1,9)); request.setMainGoal("RAG integration"); request.setStatus(LearningPlanStatus.PLANNED);
        long plan = learning.createPlan(user.getId(),request).getId();
        return new Session(user.getId(),plan,"Bearer "+tokens.createToken(user.getId(),user.getUsername()));
    }
    record Session(long user, long plan, String auth) { String base() { return "/api/v1/learning-plans/"+plan+"/materials"; } }
    MockMultipartFile file(String... paragraphs) {
        return new MockMultipartFile("file","fixture.docx",DOCX,docx(paragraphs));
    }
    LearningMaterial upload(Session owner,String... text) { return ingestion.upload(owner.plan(),owner.user(),file(text),null); }
    List<Map<String,Object>> stored(long material) {
        return jdbc.queryForList("SELECT * FROM learning_material_chunk WHERE material_id=? ORDER BY chunk_index",material);
    }

    @Test void uploadPdfAndDocxPersistOriginalsLocationsAndSafeDownload() throws Exception {
        Session a=owner();
        for (MockMultipartFile file : List.of(file("Java 21"),new MockMultipartFile("file","fixture.pdf","application/pdf",pdf()))) {
            var response=mvc.perform(multipart(a.base()+"/upload").file(file).header("Authorization",a.auth()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.indexStatus").value("READY"))
                    .andExpect(jsonPath("$.chunkCount").value(1)).andExpect(jsonPath("$.fileContent").doesNotExist())
                    .andReturn().getResponse();
            long id=json.readTree(response.getContentAsByteArray()).get("id").asLong();
            assertThat(jdbc.queryForObject("SELECT file_content FROM learning_material WHERE id=?",byte[].class,id)).isEqualTo(file.getBytes());
            var rows=stored(id); assertThat(rows).hasSize(1); assertThat(rows.getFirst().get("user_id")).isEqualTo(a.user());
            assertThat(rows.getFirst().get("page_number")).isEqualTo(file.getOriginalFilename().endsWith(".pdf")?1:null);
            mvc.perform(get(a.base()+"/"+id+"/file").header("Authorization",a.auth())).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                    .andExpect(content().bytes(file.getBytes()));
        }
    }
    @Test void mixedOwnersAreIsolatedInSqlQueryAndEverySourceMutationEndpoint() throws Exception {
        Session a=owner(), b=owner();
        var ma=upload(a,"Java 21 owner A"); var mb=upload(b,"Java 21 PRIVATE B");
        long chunkB=((Number)stored(mb.getId()).getFirst().get("id")).longValue();
        assertThat(chunks.selectCandidates(a.user(),a.plan(),"mysql-fixture-v1")).allSatisfy(c -> {
            assertThat(c.getUserId()).isEqualTo(a.user()); assertThat(c.getMaterialId()).isEqualTo(ma.getId());
        }).hasSize(1);
        var answer=rag.query(a.plan(),a.user(),new RagQueryRequest("Java?"));
        assertThat(answer.citations()).extracting(RagCitation::materialId).containsExactly(ma.getId());
        for(String base:List.of(a.base(),b.base())) {
            mvc.perform(get(base+"/"+mb.getId()+"/chunks/"+chunkB).header("Authorization",a.auth())).andExpect(status().isNotFound());
            mvc.perform(get(base+"/"+mb.getId()+"/file").header("Authorization",a.auth())).andExpect(status().isNotFound());
            mvc.perform(delete(base+"/"+mb.getId()).header("Authorization",a.auth())).andExpect(status().isNotFound());
            mvc.perform(post(base+"/"+mb.getId()+"/reindex").header("Authorization",a.auth())).andExpect(status().isNotFound());
        }
        mvc.perform(post(b.base()+"/rag/query").header("Authorization",a.auth()).contentType("application/json")
                .content("{\"question\":\"Java?\"}")).andExpect(status().isNotFound());
    }
    @Test void materialAndPlanDeletionCascadeOriginalAndChunks() {
        Session a=owner(); var one=upload(a,"Java 21"); var two=upload(a,"Java 21 second");
        learning.deleteMaterial(a.plan(),one.getId(),a.user()); assertThat(stored(one.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_material WHERE id=?",Long.class,one.getId())).isZero();
        learning.deletePlan(a.plan(),a.user()); assertThat(stored(two.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_material WHERE user_id=?",Long.class,a.user())).isZero();
    }
    @Test void reindexReplacesIdsAndProviderFailureKeepsReadySnapshot() {
        Session a=owner(); var material=upload(a,"Java first","Java second"); var original=stored(material.getId());
        ingestion.reindex(a.plan(),material.getId(),a.user()); var replaced=stored(material.getId());
        assertThat(replaced).hasSize(2); assertThat(replaced.getFirst().get("id")).isNotEqualTo(original.getFirst().get("id"));
        when(embedding.embed(anyList())).thenThrow(new AiProviderException("synthetic failure"));
        assertThatThrownBy(() -> ingestion.reindex(a.plan(),material.getId(),a.user())).isInstanceOf(AiProviderException.class);
        assertThat(stored(material.getId())).isEqualTo(replaced);
        assertThat(learning.getMaterial(a.plan(),material.getId(),a.user()).getIndexStatus()).isEqualTo("READY");
    }
    @Test void failedFirstIndexRetainsOriginalAndCanRetry() {
        Session a=owner(); when(embedding.embed(anyList())).thenThrow(new AiProviderException("synthetic failure"));
        assertThatThrownBy(() -> upload(a,"Java 21")).isInstanceOf(AiProviderException.class);
        var material=learning.listMaterials(a.plan(),a.user()).getFirst();
        assertThat(material.getIndexStatus()).isEqualTo("FAILED"); assertThat(stored(material.getId())).isEmpty();
        assertThat(ingestion.download(a.plan(),material.getId(),a.user()).bytes()).isEqualTo(docx("Java 21"));
        doReturn(List.of(new float[]{1,0})).when(embedding).embed(anyList());
        assertThat(ingestion.reindex(a.plan(),material.getId(),a.user()).getIndexStatus()).isEqualTo("READY");
    }
    @Test void secondChunkInsertFailureRollsBackReplacementInRealTransaction() {
        Session a=owner(); var material=upload(a,"Java first","Java second"); var before=stored(material.getId());
        injector.arm(material.getId());
        assertThatThrownBy(() -> ingestion.reindex(a.plan(),material.getId(),a.user())).isInstanceOf(AiProviderException.class);
        assertThat(injector.calls).isEqualTo(2); assertThat(injector.firstInserted).isTrue(); injector.disarm();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(stored(material.getId())).isEqualTo(before);
        assertThat(learning.getMaterial(a.plan(),material.getId(),a.user()).getIndexStatus()).isEqualTo("READY");
    }
    @Test void queryIsReadOnlyAndNoEvidenceDoesNotCallChat() {
        Session a=owner(); var material=upload(a,"Java 21"); Map<String,Object> before=snapshot(a.user());
        var answer=rag.query(a.plan(),a.user(),new RagQueryRequest("Java?"));
        assertThat(answer.citations().getFirst().originalExcerpt()).isEqualTo("Java 21");
        assertThat(snapshot(a.user())).isEqualTo(before);
        clearInvocations(chat); when(embedding.embed(anyList())).thenReturn(List.of(new float[]{0,1}));
        assertThat(rag.query(a.plan(),a.user(),new RagQueryRequest("SQL?"))).isEqualTo(new RagAnswer(RagService.NO_EVIDENCE,List.of(),List.of(),true));
        verify(chat,never()).answer(anyString(),anyString()); assertThat(snapshot(a.user())).isEqualTo(before);
        assertThat(stored(material.getId())).hasSize(1);
    }
    @Test void deletionDuringEmbeddingCannotResurrectMaterial() throws Exception {
        Session a=owner(); var material=upload(a,"Java 21");
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        when(embedding.embed(anyList())).thenAnswer(i->{ entered.countDown(); if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); return List.of(new float[]{1,0}); });
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            var result=executor.submit(()->ingestion.reindex(a.plan(),material.getId(),a.user()));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue(); learning.deletePlan(a.plan(),a.user()); release.countDown();
            assertThatThrownBy(()->result.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(com.careerplatform.common.exception.ResourceNotFoundException.class);
            assertThat(stored(material.getId())).isEmpty();
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    Map<String,Object> snapshot(long user) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String table:List.of("learning_plan","learning_task","study_record","weekly_review","learning_note","user_skill"))
            result.put(table,jdbc.queryForList("SELECT * FROM "+table+" WHERE user_id=? ORDER BY id",user));
        result.put("materials",jdbc.queryForList("SELECT id,title,index_status,chunk_count,HEX(file_content) AS original FROM learning_material WHERE user_id=? ORDER BY id",user));
        result.put("chunks",jdbc.queryForList("SELECT * FROM learning_material_chunk WHERE user_id=? ORDER BY id",user)); return result;
    }
    static byte[] docx(String... paragraphs) {
        StringBuilder xml=new StringBuilder("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        for(String p:paragraphs) xml.append("<w:p><w:r><w:t>").append(p).append("</w:t></w:r></w:p>");
        xml.append("</w:body></w:document>");
        try(var out=new ByteArrayOutputStream(); var zip=new ZipOutputStream(out)) {
            ZipEntry entry=new ZipEntry("word/document.xml"); entry.setTime(0); zip.putNextEntry(entry);
            zip.write(xml.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); zip.finish(); return out.toByteArray();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    static byte[] pdf() throws Exception {
        try(var doc=new org.apache.pdfbox.pdmodel.PDDocument();var out=new ByteArrayOutputStream()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage(); doc.addPage(page);
            try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,page)) {
                stream.beginText(); stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
                stream.newLineAtOffset(50,700); stream.showText("Java 21"); stream.endText();
            }
            doc.save(out); return out.toByteArray();
        }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class InjectionConfig { @Bean InsertFailure insertFailure(JdbcTemplate jdbc) { return new InsertFailure(jdbc); } }
    @Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
    static class InsertFailure implements Interceptor {
        final JdbcTemplate jdbc; final ThreadLocal<Long> material=new ThreadLocal<>(); int calls; boolean firstInserted;
        InsertFailure(JdbcTemplate jdbc) { this.jdbc=jdbc; }
        void arm(long id) { calls=0; firstInserted=false; material.set(id); }
        void disarm() { material.remove(); }
        @Override public Object intercept(Invocation invocation) throws Throwable {
            if(material.get()==null || !((MappedStatement)invocation.getArgs()[0]).getId().equals(LearningMaterialChunkMapper.class.getName()+".insert")) return invocation.proceed();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if(++calls==2) { assertThat(firstInserted).isTrue(); throw new IllegalStateException("synthetic second chunk insert failure"); }
            Object result=invocation.proceed(); assertThat(result).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_material_chunk WHERE material_id=?",Long.class,material.get())).isEqualTo(1);
            firstInserted=true; return result;
        }
    }
}
