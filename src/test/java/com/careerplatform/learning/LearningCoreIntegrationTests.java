package com.careerplatform.learning;

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

import java.time.LocalDate;
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
class LearningCoreIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void planShouldSupportOwnedCrudAndRejectDuplicateAndInvalidDates() throws Exception {
        AuthSession owner = registerAndLogin("learning_plan_owner_");
        String weekStart = "2026-09-07";
        Long planId = responseId(mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(weekStart, "2026-09-13", "Ship a Java service", "PLANNED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weekStart").value(weekStart))
                .andExpect(jsonPath("$.weekEnd").value("2026-09-13"))
                .andExpect(jsonPath("$.mainGoal").value("Ship a Java service"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.userId").doesNotExist()));

        mockMvc.perform(get("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(planId));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId))
                .andExpect(jsonPath("$.status").value("PLANNED"));

        mockMvc.perform(put("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(weekStart, "2026-09-14", "Ship the revised Java service", "IN_PROGRESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainGoal").value("Ship the revised Java service"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(weekStart, "2026-09-20", "Duplicate week", "PLANNED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        Long secondPlanId = responseId(mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("2026-09-21", "2026-09-27", "Second plan", "PLANNED")))
                .andExpect(status().isCreated()));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}", secondPlanId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(weekStart, "2026-09-27", "Duplicate on update", "PLANNED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("2026-10-01", "2026-09-30", "Invalid dates", "PLANNED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(delete("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void planShouldRejectForeignCrudAndPreserveOwnerData() throws Exception {
        AuthSession owner = registerAndLogin("learning_plan_iso_owner_");
        AuthSession other = registerAndLogin("learning_plan_iso_other_");
        Long planId = responseId(mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("2026-11-02", "2026-11-08", "Owner goal", "PLANNED")))
                .andExpect(status().isCreated()));

        assertOtherUserCannotCrud(other, "/api/v1/learning-plans", planId,
                planJson("2026-11-02", "2026-11-08", "Stolen goal", "COMPLETED"));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainGoal").value("Owner goal"))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void learningFieldBoundariesAndNumericValidationShouldRejectInvalidRequests() throws Exception {
        AuthSession owner = registerAndLogin("learning_boundary_owner_");
        Long planId = createPlan(owner, "2028-01-03", "2028-01-09", "g".repeat(500), "PLANNED");

        mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("2028-01-10", "2028-01-16", "g".repeat(501), "PLANNED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("t".repeat(200), "2028-01-03", "TODO", 1, 0,
                                "d".repeat(16_000))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("t".repeat(201), "2028-01-03", "TODO", 1, 0, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Zero minutes", "2028-01-03", "TODO", 0, 0, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Negative sort", "2028-01-03", "TODO", 1, -1, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Invalid enum", "2028-01-03", "NOT_A_STATUS", 1, 0, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiredLearningFieldsShouldReturnValidationErrorBeforeWrite() throws Exception {
        AuthSession owner = registerAndLogin("learning_required_owner_");
        Long planId = createPlan(owner, "2028-02-01", "2028-02-07", "Required fields plan", "PLANNED");

        mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TODO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/learning-tasks/999999999/records")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void putShouldReplaceOmittedNullableTaskAndRecordFieldsWithNull() throws Exception {
        AuthSession owner = registerAndLogin("learning_nullable_");
        Long planId = createPlan(owner, "2027-04-01", "2027-04-07", "Nullable replacement plan", "PLANNED");
        Long taskId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Nullable task", "2027-04-07", "TODO", 30, 2, "Keep this")))
                .andExpect(status().isCreated()));

        mockMvc.perform(put("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Replaced task\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.dueDate").doesNotExist())
                .andExpect(jsonPath("$.sortOrder").doesNotExist());

        Long recordId = responseId(mockMvc.perform(post("/api/v1/learning-tasks/{taskId}/records", taskId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson("2027-04-01T09:00:00", 25, "Keep this record")))
                .andExpect(status().isCreated()));
        mockMvc.perform(put("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studiedAt\":\"2027-04-01T10:00:00\",\"durationMinutes\":35}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void planTaskAndRecordCrudResponsesShouldExposeDatabaseTimestamps() throws Exception {
        AuthSession owner = registerAndLogin("learning_timestamp_owner_");
        ResultActions createdPlan = mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("2027-05-01", "2027-05-07", "Timestamp plan", "PLANNED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        Long planId = responseId(createdPlan);
        LocalDateTime planUpdatedAtBefore = LocalDateTime.parse(
                objectMapper.readTree(createdPlan.andReturn().getResponse().getContentAsString())
                        .get("updatedAt").asText());
        Thread.sleep(1_100);
        ResultActions updatedPlan = mockMvc.perform(put("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("2027-05-01", "2027-05-07", "Updated timestamp plan", "IN_PROGRESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        LocalDateTime planUpdatedAtAfter = LocalDateTime.parse(
                objectMapper.readTree(updatedPlan.andReturn().getResponse().getContentAsString())
                        .get("updatedAt").asText());
        assertThat(planUpdatedAtAfter).isAfter(planUpdatedAtBefore);

        Long taskId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Timestamp task", "2027-05-07", "TODO", 30, 0, "Details")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty()));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Updated timestamp task", "2027-05-07", "IN_PROGRESS", 45, 1, "Updated details")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        Long recordId = responseId(mockMvc.perform(post("/api/v1/learning-tasks/{taskId}/records", taskId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson("2027-05-01T09:00:00", 20, "Timestamp record")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty()));
        mockMvc.perform(put("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson("2027-05-01T10:00:00", 25, "Updated timestamp record")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void planUpdateShouldRejectRangeThatExcludesOwnedTaskDueDateAndPreservePlan() throws Exception {
        AuthSession owner = registerAndLogin("learning_plan_range_owner_");
        Long planId = createPlan(owner, "2027-06-01", "2027-06-07", "Keep this plan", "PLANNED");
        createTask(owner, planId,
                taskJson("Near-end task", "2027-06-07", "TODO", 30, 0, null));

        mockMvc.perform(put("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("2027-06-01", "2027-06-06", "Shrunk plan", "IN_PROGRESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/learning-plans/{planId}", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2027-06-01"))
                .andExpect(jsonPath("$.weekEnd").value("2027-06-07"))
                .andExpect(jsonPath("$.mainGoal").value("Keep this plan"))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void taskShouldRequireOwnedPlanSupportCrudAndEnforceDueDateRange() throws Exception {
        AuthSession owner = registerAndLogin("learning_task_owner_");
        AuthSession other = registerAndLogin("learning_task_other_");
        Long planId = createPlan(owner, "2026-12-07", "2026-12-13", "Task plan", "IN_PROGRESS");
        Long wrongPlanId = createPlan(owner, "2026-12-14", "2026-12-20", "Wrong path plan", "PLANNED");

        Long taskId = responseId(mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Boundary task", "2026-12-07", "TODO", 45, 0, "Read the first chapter")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Boundary task"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-07"))
                .andExpect(jsonPath("$.userId").doesNotExist()));

        mockMvc.perform(get("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(taskId));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO"));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Boundary task updated", "2026-12-13", "IN_PROGRESS", 90, 1, "Finish the chapter")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-13"));

        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Before range", "2026-12-06", "TODO", 10, 2, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("After range", "2026-12-14", "TODO", 10, 2, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Foreign plan task", "2026-12-07", "TODO", 10, 2, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/tasks/{taskId}", wrongPlanId, taskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/learning-plans/{planId}/tasks/{taskId}", wrongPlanId, taskId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Wrong path update", "2026-12-14", "DONE", 1, 1, null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/learning-plans/{planId}/tasks/{taskId}", wrongPlanId, taskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());

        assertOtherUserCannotCrud(other, "/api/v1/learning-plans/" + planId + "/tasks", taskId,
                taskJson("Stolen task", "2026-12-08", "DONE", 1, 1, null));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Boundary task updated"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(delete("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
    }

    @Test
    void taskAndRecordListsShouldUseStableBusinessOrdering() throws Exception {
        AuthSession owner = registerAndLogin("learning_ordering_owner_");
        Long planId = createPlan(owner, "2027-01-04", "2027-01-10", "Ordering plan", "PLANNED");
        Long firstTaskId = createTask(owner, planId,
                taskJson("Sort one", "2027-01-04", "TODO", 10, 1, null));
        Long zeroTaskId = createTask(owner, planId,
                taskJson("Sort zero", "2027-01-05", "TODO", 10, 0, null));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(zeroTaskId))
                .andExpect(jsonPath("$[1].id").value(firstTaskId));

        Long recordOneId = createRecord(owner, firstTaskId,
                recordJson("2027-01-04T09:00:00", 20, "Earlier"));
        Long recordTwoId = createRecord(owner, firstTaskId,
                recordJson("2027-01-04T10:00:00", 30, "Later"));
        mockMvc.perform(get("/api/v1/learning-tasks/{taskId}/records", firstTaskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(recordTwoId))
                .andExpect(jsonPath("$[1].id").value(recordOneId));
    }

    @Test
    void recordShouldSupportOwnedCrudRequireOwnedTaskAndPreserveTaskStatus() throws Exception {
        AuthSession owner = registerAndLogin("learning_record_owner_");
        AuthSession other = registerAndLogin("learning_record_other_");
        Long planId = createPlan(owner, "2027-02-01", "2027-02-07", "Record plan", "PLANNED");
        Long taskId = createTask(owner, planId,
                taskJson("Record task", "2027-02-01", "IN_PROGRESS", 60, 0, null));
        Long otherTaskId = createTask(owner, planId,
                taskJson("Other task", "2027-02-02", "TODO", 20, 1, null));

        Long recordId = responseId(mockMvc.perform(post("/api/v1/learning-tasks/{taskId}/records", taskId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson("2027-02-01T18:30:00", 30, "Solved the exercise")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.userId").doesNotExist()));

        mockMvc.perform(get("/api/v1/learning-plans/{planId}/tasks/{taskId}", planId, taskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(get("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Solved the exercise"));
        mockMvc.perform(put("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson("2027-02-01T19:00:00", 45, "Reviewed the solution")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.content").value("Reviewed the solution"));

        mockMvc.perform(post("/api/v1/learning-tasks/{taskId}/records", taskId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson("2027-02-02T19:00:00", 0, "Zero")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/learning-tasks/{taskId}/records", taskId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson("2027-02-02T19:00:00", -1, "Negative")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/learning-tasks/{taskId}/records", otherTaskId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson("2027-02-02T19:00:00", 10, "Foreign")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/learning-tasks/{taskId}/records/{recordId}", otherTaskId, recordId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
        assertOtherUserCannotCrud(other, "/api/v1/learning-tasks/" + taskId + "/records", recordId,
                recordJson("2027-02-01T20:00:00", 99, "Stolen record"));
        mockMvc.perform(get("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.content").value("Reviewed the solution"));

        mockMvc.perform(delete("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/learning-tasks/{taskId}/records/{recordId}", taskId, recordId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNotFound());
    }

    @Test
    void textAndInvalidJsonShouldReturnUnifiedValidationErrorBeforeWrite() throws Exception {
        AuthSession owner = registerAndLogin("learning_validation_owner_");
        Long planId = createPlan(owner, "2027-03-01", "2027-03-07", "Validation plan", "PLANNED");

        mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStart\":\"2027-03-08\",\"weekEnd\":\"2027-03-14\",\"mainGoal\":\"bad enum\",\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求格式无效"));
        mockMvc.perform(post("/api/v1/learning-plans")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求格式无效"));

        String oversized = "x".repeat(16_001);
        mockMvc.perform(post("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Too much text", "2027-03-01", "TODO", 10, 0, oversized)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/learning-plans/{planId}/tasks", planId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
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

    private record AuthSession(String token) {
        String authorization() {
            return "Bearer " + token;
        }
    }
}
