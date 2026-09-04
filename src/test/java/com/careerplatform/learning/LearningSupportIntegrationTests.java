package com.careerplatform.learning;

import com.careerplatform.learning.mapper.LearningMaterialMapper;
import com.careerplatform.learning.mapper.LearningNoteMapper;
import com.careerplatform.learning.mapper.LearningPlanMapper;
import com.careerplatform.learning.mapper.LearningTaskMapper;
import com.careerplatform.learning.mapper.StudyRecordMapper;
import com.careerplatform.learning.mapper.WeeklyReviewMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearningSupportIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WeeklyReviewMapper weeklyReviewMapper;

    @Autowired
    private LearningPlanMapper learningPlanMapper;

    @Autowired
    private LearningTaskMapper learningTaskMapper;

    @Autowired
    private StudyRecordMapper studyRecordMapper;

    @Autowired
    private LearningNoteMapper learningNoteMapper;

    @Autowired
    private LearningMaterialMapper learningMaterialMapper;

    @Test
    void reviewShouldCreateThenUpsertSameRowAndRejectForeignAccess() throws Exception {
        AuthSession owner = registerAndLogin("learning_review_owner_");
        AuthSession other = registerAndLogin("learning_review_other_");
        Long planId = createPlan(owner, "2027-07-05", "2027-07-11", "Review plan", "COMPLETED");

        Long reviewId = responseId(mockMvc.perform(put("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("First summary", "First achievements", "First problems",
                                "First next steps", "2027-07-11T20:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.summary").value("First summary"))
                .andExpect(jsonPath("$.reviewedAt").value("2027-07-11T20:00:00"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty()));

        Long upsertedId = responseId(mockMvc.perform(put("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Updated summary", "Updated achievements", null,
                                "Updated next steps", "2027-07-12T08:30:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reviewId))
                .andExpect(jsonPath("$.summary").value("Updated summary"))
                .andExpect(jsonPath("$.achievements").value("Updated achievements"))
                .andExpect(jsonPath("$.problems").doesNotExist())
                .andExpect(jsonPath("$.reviewedAt").value("2027-07-12T08:30:00")));
        assertThat(upsertedId).isEqualTo(reviewId);
        assertThat(weeklyReviewMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.careerplatform.learning.entity.WeeklyReview>()
                        .eq(com.careerplatform.learning.entity.WeeklyReview::getPlanId, planId))).isEqualTo(1);

        mockMvc.perform(get("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reviewId))
                .andExpect(jsonPath("$.summary").value("Updated summary"));

        mockMvc.perform(get("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Stolen summary", null, null, null, "2027-07-13T08:30:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Updated summary"));

        mockMvc.perform(put("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Missing timestamp\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void reviewTextBoundaryShouldRejectOversizedTextBeforeDatabaseWrite() throws Exception {
        AuthSession owner = registerAndLogin("learning_review_boundary_");
        Long planId = createPlan(owner, "2028-03-06", "2028-03-12", "Review boundary plan", "PLANNED");

        mockMvc.perform(put("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("x".repeat(16_001), null, null, null,
                                "2028-03-12T20:00:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(weeklyReviewMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.careerplatform.learning.entity.WeeklyReview>()
                        .eq(com.careerplatform.learning.entity.WeeklyReview::getPlanId, planId))).isZero();
    }

    @Test
    void noteShouldSupportCrudAndRequireTaskToBelongToPlanAndOwner() throws Exception {
        AuthSession owner = registerAndLogin("learning_note_owner_");
        AuthSession other = registerAndLogin("learning_note_other_");
        Long planId = createPlan(owner, "2027-08-02", "2027-08-08", "Note plan", "IN_PROGRESS");
        Long otherPlanId = createPlan(owner, "2027-08-09", "2027-08-15", "Other plan", "PLANNED");
        Long foreignPlanId = createPlan(other, "2027-08-16", "2027-08-22", "Foreign plan", "PLANNED");
        Long taskId = createTask(owner, planId,
                taskJson("Note task", "2027-08-02", "TODO", 30, 0, null));
        Long samePlanTaskId = createTask(owner, planId,
                taskJson("Second note task", "2027-08-03", "TODO", 30, 1, null));
        Long crossPlanTaskId = createTask(owner, otherPlanId,
                taskJson("Cross-plan task", "2027-08-09", "TODO", 30, 0, null));
        Long foreignTaskId = createTask(other, foreignPlanId,
                taskJson("Foreign task", "2027-08-16", "TODO", 30, 0, null));

        Long unassignedNoteId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/notes", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(null, "Unassigned note", "Keep this note")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.taskId").doesNotExist())
                .andExpect(jsonPath("$.title").value("Unassigned note"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty()));
        Long assignedNoteId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/notes", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(taskId, "Assigned note", "Read this chapter")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").value(taskId)));

        mockMvc.perform(get("/api/v1/learning-plans/{planId}/notes", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(assignedNoteId))
                .andExpect(jsonPath("$[1].id").value(unassignedNoteId));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, assignedNoteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));

        mockMvc.perform(put("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, assignedNoteId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(samePlanTaskId, "Updated note", "Updated content")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(samePlanTaskId))
                .andExpect(jsonPath("$.title").value("Updated note"));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, assignedNoteId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(crossPlanTaskId, "Wrong task", "Must not write")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, assignedNoteId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(foreignTaskId, "Wrong owner", "Must not write")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/learning-plans/{planId}/notes", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(crossPlanTaskId, "Cross-plan task", "Must not write")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/notes", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(foreignTaskId, "Foreign task", "Must not write")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertOtherUserCannotCrud(other, "/api/v1/learning-plans/" + planId + "/notes", unassignedNoteId,
                noteJson(taskId, "Stolen note", "Must not write"));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, unassignedNoteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Unassigned note"))
                .andExpect(jsonPath("$.content").value("Keep this note"));

        mockMvc.perform(post("/api/v1/learning-plans/{planId}/notes", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(null, "Too long", "x".repeat(16_001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(delete("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, assignedNoteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, assignedNoteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
    }

    @Test
    void materialShouldSupportNullableSourceUrlCrudAndRequireTaskToBelongToPlanAndOwner() throws Exception {
        AuthSession owner = registerAndLogin("learning_material_owner_");
        AuthSession other = registerAndLogin("learning_material_other_");
        Long planId = createPlan(owner, "2027-09-06", "2027-09-12", "Material plan", "IN_PROGRESS");
        Long otherPlanId = createPlan(owner, "2027-09-13", "2027-09-19", "Other material plan", "PLANNED");
        Long foreignPlanId = createPlan(other, "2027-09-20", "2027-09-26", "Foreign material plan", "PLANNED");
        Long taskId = createTask(owner, planId,
                taskJson("Material task", "2027-09-06", "TODO", 30, 0, null));
        Long crossPlanTaskId = createTask(owner, otherPlanId,
                taskJson("Cross-plan material task", "2027-09-13", "TODO", 30, 0, null));
        Long foreignTaskId = createTask(other, foreignPlanId,
                taskJson("Foreign material task", "2027-09-20", "TODO", 30, 0, null));

        Long unassignedMaterialId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/materials", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(null, "Material without URL", null, "Metadata only")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.taskId").doesNotExist())
                .andExpect(jsonPath("$.sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist()));
        Long assignedMaterialId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/materials", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(taskId, "Assigned material", "https://example.test/course", "Course notes")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.test/course")));

        mockMvc.perform(get("/api/v1/learning-plans/{planId}/materials", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(assignedMaterialId))
                .andExpect(jsonPath("$[1].id").value(unassignedMaterialId));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, assignedMaterialId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Assigned material"));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, assignedMaterialId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(null, "Updated material", null, "Updated metadata")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").doesNotExist())
                .andExpect(jsonPath("$.sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.description").value("Updated metadata"));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, assignedMaterialId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(crossPlanTaskId, "Wrong task", null, "Must not write")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, assignedMaterialId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(foreignTaskId, "Wrong owner", null, "Must not write")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/learning-plans/{planId}/materials", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(crossPlanTaskId, "Cross-plan task", null, "Must not write")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/materials", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(foreignTaskId, "Foreign task", null, "Must not write")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertOtherUserCannotCrud(other, "/api/v1/learning-plans/" + planId + "/materials", unassignedMaterialId,
                materialJson(null, "Stolen material", null, "Must not write"));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, unassignedMaterialId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Material without URL"));

        mockMvc.perform(post("/api/v1/learning-plans/{planId}/materials", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(null, "Long URL", "u".repeat(2_049), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/materials", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(null, "Long description", null, "x".repeat(16_001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(delete("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, assignedMaterialId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, assignedMaterialId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingTaskShouldUnbindSupportResourcesAndDeletingPlanShouldRemoveAllDescendants() throws Exception {
        AuthSession owner = registerAndLogin("learning_delete_owner_");
        Long planId = createPlan(owner, "2027-10-04", "2027-10-10", "Delete plan", "IN_PROGRESS");
        Long taskId = createTask(owner, planId,
                taskJson("Delete task", "2027-10-04", "TODO", 30, 0, null));
        Long recordId = createRecord(owner, taskId,
                recordJson("2027-10-04T10:00:00", 30, "Record to delete"));
        Long noteId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/notes", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(taskId, "Note to unbind", "Preserve me")))
                .andExpect(status().isCreated()));
        Long materialId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/materials", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(taskId, "Material to unbind", null, "Preserve me")))
                .andExpect(status().isCreated()));
        Long reviewId = responseId(mockMvc.perform(put("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Delete review", null, null, null, "2027-10-10T20:00:00")))
                .andExpect(status().isOk()));

        mockMvc.perform(delete("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, noteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").doesNotExist());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, materialId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").doesNotExist());

        mockMvc.perform(delete("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, noteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, materialId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        assertThat(reviewId).isNotNull();
    }

    @Test
    void deletingSecondCompletePlanAggregateShouldRemoveEveryDescendant() throws Exception {
        AuthSession owner = registerAndLogin("learning_complete_");
        Long planId = createPlan(owner, "2027-12-06", "2027-12-12", "Complete delete plan", "COMPLETED");
        Long taskId = createTask(owner, planId,
                taskJson("Complete delete task", "2027-12-06", "DONE", 45, 0, "Task details"));
        Long recordId = createRecord(owner, taskId,
                recordJson("2027-12-06T09:00:00", 45, "Record details"));
        Long reviewId = responseId(mockMvc.perform(put("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("Complete review", "Achievement", "Problem", "Next step",
                                "2027-12-12T20:00:00")))
                .andExpect(status().isOk()));
        Long noteId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/notes", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson(taskId, "Complete note", "Note details")))
                .andExpect(status().isCreated()));
        Long materialId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/materials", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialJson(taskId, "Complete material", "https://example.test/complete",
                                "Material details")))
                .andExpect(status().isCreated()));

        mockMvc.perform(delete("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());

        assertThat(learningPlanMapper.selectById(planId)).isNull();
        assertThat(learningTaskMapper.selectById(taskId)).isNull();
        assertThat(studyRecordMapper.selectById(recordId)).isNull();
        assertThat(weeklyReviewMapper.selectById(reviewId)).isNull();
        assertThat(learningNoteMapper.selectById(noteId)).isNull();
        assertThat(learningMaterialMapper.selectById(materialId)).isNull();

        mockMvc.perform(get("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/review", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/notes/{noteId}", planId, noteId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/materials/{materialId}", planId, materialId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
    }

    private Long createPlan(AuthSession user, String weekStart, String weekEnd,
                            String mainGoal, String status) throws Exception {
        return responseId(mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(weekStart, weekEnd, mainGoal, status)))
                .andExpect(status().isCreated()));
    }

    private Long createTask(AuthSession user, Long planId, String json) throws Exception {
        return responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated()));
    }

    private Long createRecord(AuthSession user, Long taskId, String json) throws Exception {
        return responseId(mockMvc.perform(post("/api/v1/learning-tasks/{taskId}/records", taskId)
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated()));
    }

    private void assertOtherUserCannotCrud(AuthSession other, String basePath, Long id,
                                            String updateJson) throws Exception {
        mockMvc.perform(get(basePath + "/{id}", id)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put(basePath + "/{id}", id)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete(basePath + "/{id}", id)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private AuthSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String password = "correct-password";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new AuthSession(objectMapper.readTree(login).get("token").asText());
    }

    private Long responseId(ResultActions resultActions) throws Exception {
        JsonNode response = objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private String planJson(String weekStart, String weekEnd, String mainGoal, String status) {
        return "{\"weekStart\":\"%s\",\"weekEnd\":\"%s\",\"mainGoal\":\"%s\",\"status\":\"%s\"}"
                .formatted(weekStart, weekEnd, mainGoal, status);
    }

    private String taskJson(String title, String dueDate, String status, int plannedMinutes,
                            int sortOrder, String description) {
        String descriptionField = description == null ? "" : ",\"description\":\"" + description + "\"";
        return "{\"title\":\"%s\"%s,\"status\":\"%s\",\"plannedMinutes\":%d,\"dueDate\":\"%s\",\"sortOrder\":%d}"
                .formatted(title, descriptionField, status, plannedMinutes, dueDate, sortOrder);
    }

    private String recordJson(String studiedAt, int durationMinutes, String content) {
        return "{\"studiedAt\":\"%s\",\"durationMinutes\":%d,\"content\":\"%s\"}"
                .formatted(studiedAt, durationMinutes, content);
    }

    private String reviewJson(String summary, String achievements, String problems,
                              String nextSteps, String reviewedAt) {
        StringBuilder json = new StringBuilder("{");
        appendJsonField(json, "summary", summary);
        appendJsonField(json, "achievements", achievements);
        appendJsonField(json, "problems", problems);
        appendJsonField(json, "nextSteps", nextSteps);
        appendJsonField(json, "reviewedAt", reviewedAt);
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }
        return json.append('}').toString();
    }

    private String noteJson(Long taskId, String title, String content) {
        StringBuilder json = new StringBuilder("{");
        if (taskId != null) {
            json.append("\"taskId\":").append(taskId).append(',');
        }
        appendJsonField(json, "title", title);
        appendJsonField(json, "content", content);
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }
        return json.append('}').toString();
    }

    private String materialJson(Long taskId, String title, String sourceUrl, String description) {
        StringBuilder json = new StringBuilder("{");
        if (taskId != null) {
            json.append("\"taskId\":").append(taskId).append(',');
        }
        appendJsonField(json, "title", title);
        appendJsonField(json, "sourceUrl", sourceUrl);
        appendJsonField(json, "description", description);
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }
        return json.append('}').toString();
    }

    private void appendJsonField(StringBuilder json, String name, String value) {
        if (value != null) {
            json.append('"').append(name).append("\":\"").append(value).append("\",");
        }
    }

    private record AuthSession(String token) {
        String authorization() {
            return "Bearer " + token;
        }
    }
}
