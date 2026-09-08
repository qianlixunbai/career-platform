package com.careerplatform.resume;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerplatform.common.exception.InvalidResourceStateException;
import com.careerplatform.profile.entity.Skill;
import com.careerplatform.profile.mapper.SkillMapper;
import com.careerplatform.resume.dto.ResumeContentItemRequest;
import com.careerplatform.resume.dto.ResumeVersionRequest;
import com.careerplatform.resume.entity.ResumeContentItem;
import com.careerplatform.resume.entity.ResumeVersion;
import com.careerplatform.resume.enums.ResumeSectionType;
import com.careerplatform.resume.enums.ResumeVersionStatus;
import com.careerplatform.resume.mapper.ResumeContentItemMapper;
import com.careerplatform.resume.mapper.ResumeVersionMapper;
import com.careerplatform.resume.service.ResumeService;
import com.careerplatform.user.mapper.AppUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResumeIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeVersionMapper resumeVersionMapper;

    @Autowired
    private ResumeContentItemMapper resumeContentItemMapper;

    @Autowired
    private SkillMapper skillMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<Long> createdUserIds = new HashSet<>();
    private final Set<Long> createdSkillIds = new HashSet<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            // Resume children must be removed in foreign-key order.
            jdbcTemplate.update("DELETE FROM resume_file WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM resume_content_item WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM resume_version WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM resume WHERE user_id = ?", userId);

            jdbcTemplate.update("DELETE FROM user_skill WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM education_experience WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM project_experience WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM internship_experience WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM certificate_award WHERE user_id = ?", userId);
            appUserMapper.deleteById(userId);
        }
        for (Long skillId : createdSkillIds) {
            jdbcTemplate.update("DELETE FROM skill WHERE id = ?", skillId);
        }
    }

    @Test
    void resumeShouldSupportOwnedCreateListDetailUpdateAndDelete() throws Exception {
        AuthSession owner = registerAndLogin("resume_crud_");

        JsonNode created = responseJson(mockMvc.perform(post("/api/v1/resumes")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resumeJson("Backend Resume", "First draft")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Backend Resume"))
                .andExpect(jsonPath("$.description").value("First draft"))
                .andExpect(jsonPath("$.userId").doesNotExist()));
        Long resumeId = created.get("id").asLong();

        mockMvc.perform(get("/api/v1/resumes").header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(resumeId))
                .andExpect(jsonPath("$[0].name").value("Backend Resume"));
        mockMvc.perform(get("/api/v1/resumes/{resumeId}", resumeId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("First draft"));

        mockMvc.perform(put("/api/v1/resumes/{resumeId}", resumeId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resumeJson("Backend Resume Updated", "Reviewed draft")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Backend Resume Updated"))
                .andExpect(jsonPath("$.description").value("Reviewed draft"));

        mockMvc.perform(delete("/api/v1/resumes/{resumeId}", resumeId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/resumes/{resumeId}", resumeId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void emptyDraftVersionShouldSupportCreateListDetailAndEmptyItemList() throws Exception {
        AuthSession owner = registerAndLogin("resume_draft_");
        Long resumeId = createResume(owner, "Draft Resume", "No generated content yet");

        JsonNode created = responseJson(mockMvc.perform(post("/api/v1/resumes/{resumeId}/versions", resumeId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionJson("Initial")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.finalizedAt").doesNotExist()));
        Long versionId = created.get("id").asLong();

        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions", resumeId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(versionId))
                .andExpect(jsonPath("$[0].label").value("Initial"));
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}", resumeId, versionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/items", resumeId, versionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void draftItemsShouldSupportCreateListDetailUpdateAndDelete() throws Exception {
        AuthSession owner = registerAndLogin("resume_item_crud_");
        Long resumeId = createResume(owner, "Item Resume", "Item CRUD");
        Long versionId = createVersion(owner, resumeId, "Draft");

        JsonNode created = responseJson(mockMvc.perform(post("/api/v1/resumes/{resumeId}/versions/{versionId}/items",
                        resumeId, versionId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("PROFILE", "Summary", "Initial summary", 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionId").value(versionId))
                .andExpect(jsonPath("$.content").value("Initial summary"))
                .andExpect(jsonPath("$.sourceType").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist()));
        Long itemId = created.get("id").asLong();

        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/items", resumeId, versionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(itemId));
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Summary"));

        mockMvc.perform(put("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("PROJECT", "Updated summary", "Updated content", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionType").value("PROJECT"))
                .andExpect(jsonPath("$.content").value("Updated content"));

        mockMvc.perform(delete("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void generatedVersionShouldPersistSharedSnapshotAndRemainIndependentAfterFinalize() throws Exception {
        AuthSession owner = registerAndLogin("resume_snapshot_");
        Long resumeId = createResume(owner, "Snapshot Resume", "Shared profile snapshot");
        Long profileId = responseJson(mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson("Alice Snapshot", "13800138000", "Shanghai")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice Snapshot")))
                .get("id").asLong();
        Long projectId = responseJson(mockMvc.perform(post("/api/v1/project-experiences")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Resume Builder", "Developer", "Snapshot project")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectName").value("Resume Builder")))
                .get("id").asLong();
        Long skillId = responseJson(mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Snapshot Java\"}"))
                .andExpect(status().isCreated()))
                .get("id").asLong();
        createdSkillIds.add(skillId);
        Long userSkillId = responseJson(mockMvc.perform(post("/api/v1/user-skills")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillId\":" + skillId + ",\"proficiency\":\"PROFICIENT\"}"))
                .andExpect(status().isCreated()))
                .get("id").asLong();

        Long versionId = responseJson(mockMvc.perform(post("/api/v1/resumes/{resumeId}/versions/generate", resumeId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionJson("Generated from profile")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")))
                .get("id").asLong();
        JsonNode beforeFinalize = responseJson(mockMvc.perform(get(
                        "/api/v1/resumes/{resumeId}/versions/{versionId}/items", resumeId, versionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()));
        assertThat(beforeFinalize).hasSize(3);
        JsonNode profileItem = itemWithSourceType(beforeFinalize, "PROFILE");
        JsonNode projectItem = itemWithSourceType(beforeFinalize, "PROJECT");
        JsonNode skillItem = itemWithSourceType(beforeFinalize, "SKILL");
        assertThat(profileItem.get("sourceId").asLong()).isEqualTo(profileId);
        assertThat(projectItem.get("sourceId").asLong()).isEqualTo(projectId);
        assertThat(skillItem.get("sourceId").asLong()).isEqualTo(userSkillId);
        String profileContent = profileItem.get("content").asText();
        String projectContent = projectItem.get("content").asText();
        String skillContent = skillItem.get("content").asText();

        JsonNode finalized = responseJson(mockMvc.perform(post(
                        "/api/v1/resumes/{resumeId}/versions/{versionId}/finalize", resumeId, versionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED")));
        assertThat(finalized.get("finalizedAt").asText()).isNotBlank();

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson("Alice Changed", "13900139000", "Beijing")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/project-experiences/{id}", projectId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Resume Builder Changed", "Lead Developer", "Changed project")))
                .andExpect(status().isOk());
        Skill changedSkill = new Skill();
        changedSkill.setId(skillId);
        changedSkill.setName("Snapshot Java Changed");
        skillMapper.updateById(changedSkill);

        assertSnapshotItemUnchanged(owner, resumeId, versionId, profileItem.get("id").asLong(), profileContent);
        assertSnapshotItemUnchanged(owner, resumeId, versionId, projectItem.get("id").asLong(), projectContent);
        assertSnapshotItemUnchanged(owner, resumeId, versionId, skillItem.get("id").asLong(), skillContent);
    }

    @Test
    void copyFinalizedVersionShouldCreateIndependentDraftVersionAndItems() throws Exception {
        AuthSession owner = registerAndLogin("resume_copy_");
        Long resumeId = createResume(owner, "Copy Resume", "Copy finalized version");
        Long sourceVersionId = createVersion(owner, resumeId, "Source");
        Long sourceItemId = createItem(owner, resumeId, sourceVersionId, "PROJECT", "Source item", "Original", 0);
        mockMvc.perform(post("/api/v1/resumes/{resumeId}/versions/{versionId}/finalize", resumeId, sourceVersionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));

        JsonNode copied = responseJson(mockMvc.perform(post(
                        "/api/v1/resumes/{resumeId}/versions/{versionId}/copy", resumeId, sourceVersionId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionJson("Copied draft")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.versionNo").value(2)));
        Long targetVersionId = copied.get("id").asLong();
        assertThat(targetVersionId).isNotEqualTo(sourceVersionId);

        JsonNode targetItems = responseJson(mockMvc.perform(get(
                        "/api/v1/resumes/{resumeId}/versions/{versionId}/items", resumeId, targetVersionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()));
        assertThat(targetItems).hasSize(1);
        Long targetItemId = targetItems.get(0).get("id").asLong();
        assertThat(targetItemId).isNotEqualTo(sourceItemId);
        assertThat(targetItems.get(0).get("content").asText()).isEqualTo("Original");

        mockMvc.perform(put("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, targetVersionId, targetItemId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("PROJECT", "Target item", "Target changed", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Target changed"));
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, sourceVersionId, sourceItemId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Original"));
    }

    @Test
    void crossUserAndWrongParentAccessShould404WithoutChangingOwnedResumeVersionOrItem() throws Exception {
        AuthSession owner = registerAndLogin("resume_owner_");
        AuthSession other = registerAndLogin("resume_other_");
        Long resumeId = createResume(owner, "Owner Resume", "Private resume");
        Long wrongResumeId = createResume(owner, "Second Resume", "Different parent");
        Long versionId = createVersion(owner, resumeId, "Owner version");
        Long wrongVersionId = createVersion(owner, wrongResumeId, "Other version");
        Long itemId = createItem(owner, resumeId, versionId, "PROFILE", "Owner item", "Owner content", 0);

        String ownerResumePath = "/api/v1/resumes/{resumeId}";
        mockMvc.perform(get(ownerResumePath, resumeId).header("Authorization", other.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(ownerResumePath, resumeId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resumeJson("Stolen", "Must not update")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ownerResumePath, resumeId).header("Authorization", other.authorization()))
                .andExpect(status().isNotFound());
        assertResumeStillOwned(owner, resumeId, "Owner Resume", "Private resume");

        assertVersionPathNotFound(other, resumeId, versionId, "Other update");
        assertVersionPathNotFound(owner, wrongResumeId, versionId, "Wrong parent update");
        assertVersionPathNotFound(owner, resumeId, wrongVersionId, "Wrong version parent update");
        assertThat(responseJson(mockMvc.perform(get(
                        "/api/v1/resumes/{resumeId}/versions/{versionId}", resumeId, versionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()))).isNotNull();

        assertItemPathNotFound(other, resumeId, versionId, itemId, "Other item update");
        assertItemPathNotFound(owner, wrongResumeId, versionId, itemId, "Wrong resume item update");
        assertItemPathNotFound(owner, resumeId, wrongVersionId, itemId, "Wrong version item update");
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Owner content"));
    }

    @Test
    void finalizedVersionShouldRejectEveryWritePreserveDataAndKeepFinalizeTimestampIdempotent() throws Exception {
        AuthSession owner = registerAndLogin("resume_finalized_");
        Long resumeId = createResume(owner, "Finalized Resume", "Protected data");
        Long versionId = createVersion(owner, resumeId, "Protected version");
        Long itemId = createItem(owner, resumeId, versionId, "PROFILE", "Protected item", "Protected content", 0);

        JsonNode firstFinalize = responseJson(mockMvc.perform(post(
                        "/api/v1/resumes/{resumeId}/versions/{versionId}/finalize", resumeId, versionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED")));
        String finalizedAt = firstFinalize.get("finalizedAt").asText();
        JsonNode secondFinalize = responseJson(mockMvc.perform(post(
                        "/api/v1/resumes/{resumeId}/versions/{versionId}/finalize", resumeId, versionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED")));
        assertThat(secondFinalize.get("finalizedAt").asText()).isEqualTo(finalizedAt);

        expectInvalidState(mockMvc.perform(put("/api/v1/resumes/{resumeId}/versions/{versionId}",
                        resumeId, versionId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionJson("Must not update"))));
        expectInvalidState(mockMvc.perform(post("/api/v1/resumes/{resumeId}/versions/{versionId}/items",
                        resumeId, versionId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("PROJECT", "New item", "Must not create", 1))));
        expectInvalidState(mockMvc.perform(put("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("PROJECT", "Changed item", "Must not update", 1))));
        expectInvalidState(mockMvc.perform(delete(
                        "/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}", resumeId, versionId, itemId)
                        .header("Authorization", owner.authorization())));
        expectInvalidState(mockMvc.perform(delete("/api/v1/resumes/{resumeId}/versions/{versionId}",
                        resumeId, versionId)
                        .header("Authorization", owner.authorization())));

        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}", resumeId, versionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Protected version"))
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.finalizedAt").value(finalizedAt));
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Protected content"));
        mockMvc.perform(delete("/api/v1/resumes/{resumeId}", resumeId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
        assertResumeStillOwned(owner, resumeId, "Finalized Resume", "Protected data");
    }

    @Test
    void concurrentVersionCreatesOnOneResumeShouldProduceUniqueVersionNumbers() throws Exception {
        AuthSession owner = registerAndLogin("resume_create_race_");
        Long resumeId = createResume(owner, "Concurrent Resume", "Version race");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<ResumeVersion> first = workers.submit(() -> createVersionAfterBarrier(
                    ready, go, resumeId, owner.userId(), "Race A"));
            Future<ResumeVersion> second = workers.submit(() -> createVersionAfterBarrier(
                    ready, go, resumeId, owner.userId(), "Race B"));
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            ResumeVersion firstVersion = getFuture(first);
            ResumeVersion secondVersion = getFuture(second);
            assertThat(firstVersion.getId()).isNotEqualTo(secondVersion.getId());
            assertThat(List.of(firstVersion.getVersionNo(), secondVersion.getVersionNo()))
                    .containsExactlyInAnyOrder(1, 2);
        } finally {
            go.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        List<ResumeVersion> versions = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getUserId, owner.userId())
                .orderByAsc(ResumeVersion::getVersionNo));
        assertThat(versions).hasSize(2);
        assertThat(versions).extracting(ResumeVersion::getVersionNo).containsExactly(1, 2);
    }

    @Test
    void finalizeAndItemWriteRaceMustNeverAllowWriteAfterFinalization() throws Exception {
        AuthSession owner = registerAndLogin("resume_finalize_race_");
        Long resumeId = createResume(owner, "Finalize Race Resume", "Serialized write race");
        Long versionId = createVersion(owner, resumeId, "Race version");
        Long itemId = createItem(owner, resumeId, versionId, "PROFILE", "Race item", "Before race", 0);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<InvocationResult<ResumeVersion>> finalizeFuture = workers.submit(() -> {
                awaitRaceBarrier(ready, go);
                try {
                    return new InvocationResult<>(resumeService.finalizeVersion(resumeId, versionId, owner.userId()), null);
                } catch (Throwable failure) {
                    return new InvocationResult<>(null, failure);
                }
            });
            Future<InvocationResult<ResumeContentItem>> itemFuture = workers.submit(() -> {
                awaitRaceBarrier(ready, go);
                try {
                    return new InvocationResult<>(resumeService.updateItem(resumeId, versionId, itemId, owner.userId(),
                            itemRequest("PROFILE", "Race item", "After race", 0)), null);
                } catch (Throwable failure) {
                    return new InvocationResult<>(null, failure);
                }
            });
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            InvocationResult<ResumeVersion> finalizeResult = getFuture(finalizeFuture);
            InvocationResult<ResumeContentItem> itemResult = getFuture(itemFuture);

            assertThat(finalizeResult.failure()).isNull();
            assertThat(finalizeResult.value().getStatus()).isEqualTo(ResumeVersionStatus.FINALIZED);
            if (itemResult.failure() == null) {
                assertThat(itemResult.value().getContent()).isEqualTo("After race");
            } else {
                assertThat(itemResult.failure()).isInstanceOf(InvalidResourceStateException.class);
            }

            ResumeVersion persistedVersion = resumeVersionMapper.selectById(versionId);
            ResumeContentItem persistedItem = resumeContentItemMapper.selectById(itemId);
            assertThat(persistedVersion.getStatus()).isEqualTo(ResumeVersionStatus.FINALIZED);
            if (itemResult.failure() != null) {
                assertThat(persistedItem.getContent()).isEqualTo("Before race");
            } else {
                // The item transaction acquired the parent lock first; finalize then followed it.
                assertThat(persistedItem.getContent()).isEqualTo("After race");
            }
        } finally {
            go.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertSnapshotItemUnchanged(AuthSession owner, Long resumeId, Long versionId,
                                             Long itemId, String expectedContent) throws Exception {
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(expectedContent));
    }

    private void assertResumeStillOwned(AuthSession owner, Long resumeId, String name, String description)
            throws Exception {
        mockMvc.perform(get("/api/v1/resumes/{resumeId}", resumeId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value(description));
    }

    private void assertVersionPathNotFound(AuthSession user, Long resumeId, Long versionId, String label)
            throws Exception {
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}", resumeId, versionId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/resumes/{resumeId}/versions/{versionId}", resumeId, versionId)
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionJson(label)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/resumes/{resumeId}/versions/{versionId}", resumeId, versionId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private void assertItemPathNotFound(AuthSession user, Long resumeId, Long versionId, Long itemId,
                                        String label) throws Exception {
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("PROJECT", label, "Must not update", 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/resumes/{resumeId}/versions/{versionId}/items/{itemId}",
                        resumeId, versionId, itemId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private void expectInvalidState(ResultActions resultActions) throws Exception {
        resultActions.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));
    }

    private ResumeVersion createVersionAfterBarrier(CountDownLatch ready, CountDownLatch go,
                                                    Long resumeId, Long userId, String label) {
        awaitRaceBarrier(ready, go);
        return resumeService.createVersion(resumeId, userId, versionRequest(label));
    }

    private void awaitRaceBarrier(CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            if (!go.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Race barrier was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Race barrier interrupted", exception);
        }
    }

    private <T> T getFuture(Future<T> future) throws Exception {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw new AssertionError("Concurrent operation failed", cause);
        }
    }

    private AuthSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String password = "correct-password";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        JsonNode login = responseJson(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()));
        Long userId = login.get("userId").asLong();
        createdUserIds.add(userId);
        return new AuthSession(login.get("token").asText(), userId);
    }

    private Long createResume(AuthSession owner, String name, String description) throws Exception {
        return responseJson(mockMvc.perform(post("/api/v1/resumes")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resumeJson(name, description)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private Long createVersion(AuthSession owner, Long resumeId, String label) throws Exception {
        return responseJson(mockMvc.perform(post("/api/v1/resumes/{resumeId}/versions", resumeId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionJson(label)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private Long createItem(AuthSession owner, Long resumeId, Long versionId, String sectionType,
                            String title, String content, int sortOrder) throws Exception {
        return responseJson(mockMvc.perform(post(
                        "/api/v1/resumes/{resumeId}/versions/{versionId}/items", resumeId, versionId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(sectionType, title, content, sortOrder)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode responseJson(ResultActions resultActions) throws Exception {
        MvcResult result = resultActions.andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode itemWithSourceType(JsonNode items, String sourceType) {
        for (JsonNode item : items) {
            if (sourceType.equals(item.path("sourceType").asText())) {
                return item;
            }
        }
        throw new AssertionError("No generated item with sourceType " + sourceType + ": " + items);
    }

    private ResumeVersionRequest versionRequest(String label) {
        ResumeVersionRequest request = new ResumeVersionRequest();
        request.setLabel(label);
        return request;
    }

    private ResumeContentItemRequest itemRequest(String sectionType, String title, String content, int sortOrder) {
        ResumeContentItemRequest request = new ResumeContentItemRequest();
        request.setSectionType(ResumeSectionType.valueOf(sectionType));
        request.setTitle(title);
        request.setContent(content);
        request.setSortOrder(sortOrder);
        return request;
    }

    private String resumeJson(String name, String description) {
        return "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}";
    }

    private String versionJson(String label) {
        return "{\"label\":\"" + label + "\"}";
    }

    private String itemJson(String sectionType, String title, String content, int sortOrder) {
        return "{\"sectionType\":\"" + sectionType + "\",\"title\":\"" + title
                + "\",\"content\":\"" + content + "\",\"sortOrder\":" + sortOrder + "}";
    }

    private String profileJson(String fullName, String phone, String city) {
        return "{\"fullName\":\"" + fullName + "\",\"phone\":\"" + phone
                + "\",\"currentCity\":\"" + city + "\",\"githubUrl\":\"https://github.com/snapshot\"}";
    }

    private String projectJson(String projectName, String role, String description) {
        return "{\"projectName\":\"" + projectName + "\",\"role\":\"" + role
                + "\",\"startDate\":\"2025-01-01\",\"description\":\"" + description
                + "\",\"techStack\":\"Java, MySQL\",\"projectUrl\":\"https://example.com/snapshot\"}";
    }

    private record AuthSession(String token, Long userId) {
        String authorization() {
            return "Bearer " + token;
        }
    }

    private record InvocationResult<T>(T value, Throwable failure) {
    }
}
