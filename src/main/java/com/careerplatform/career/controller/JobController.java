package com.careerplatform.career.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.career.dto.JobNoteRequest;
import com.careerplatform.career.dto.JobNoteResponse;
import com.careerplatform.career.dto.JobRequirementRequest;
import com.careerplatform.career.dto.JobRequirementResponse;
import com.careerplatform.career.dto.JobRequest;
import com.careerplatform.career.dto.JobResponse;
import com.careerplatform.career.entity.Job;
import com.careerplatform.career.entity.JobNote;
import com.careerplatform.career.entity.JobRequirement;
import com.careerplatform.career.service.CareerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final CareerService careerService;
    public JobController(CareerService careerService) { this.careerService = careerService; }

    @PostMapping public ResponseEntity<JobResponse> create(@CurrentUserId Long currentUserId, @Valid @RequestBody JobRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(careerService.createJob(currentUserId, request))); }
    @GetMapping public List<JobResponse> list(@CurrentUserId Long currentUserId, @RequestParam(defaultValue = "false") boolean archived) { return careerService.listJobs(currentUserId, archived).stream().map(this::toResponse).toList(); }
    @GetMapping("/{id}") public JobResponse get(@PathVariable Long id, @CurrentUserId Long currentUserId) { return toResponse(careerService.getJob(id, currentUserId)); }
    @PutMapping("/{id}") public JobResponse update(@PathVariable Long id, @CurrentUserId Long currentUserId, @Valid @RequestBody JobRequest request) { return toResponse(careerService.updateJob(id, currentUserId, request)); }
    @PatchMapping("/{id}/archive") public JobResponse archive(@PathVariable Long id, @CurrentUserId Long currentUserId) { return toResponse(careerService.setArchived(id, currentUserId, true)); }
    @PatchMapping("/{id}/unarchive") public JobResponse unarchive(@PathVariable Long id, @CurrentUserId Long currentUserId) { return toResponse(careerService.setArchived(id, currentUserId, false)); }

    @PostMapping("/{jobId}/requirements") public ResponseEntity<JobRequirementResponse> createRequirement(@PathVariable Long jobId, @CurrentUserId Long currentUserId, @Valid @RequestBody JobRequirementRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(careerService.createRequirement(jobId, currentUserId, request))); }
    @GetMapping("/{jobId}/requirements") public List<JobRequirementResponse> listRequirements(@PathVariable Long jobId, @CurrentUserId Long currentUserId) { return careerService.listRequirements(jobId, currentUserId).stream().map(this::toResponse).toList(); }
    @GetMapping("/{jobId}/requirements/{id}") public JobRequirementResponse getRequirement(@PathVariable Long jobId, @PathVariable Long id, @CurrentUserId Long currentUserId) { return toResponse(careerService.getRequirement(jobId, id, currentUserId)); }
    @PutMapping("/{jobId}/requirements/{id}") public JobRequirementResponse updateRequirement(@PathVariable Long jobId, @PathVariable Long id, @CurrentUserId Long currentUserId, @Valid @RequestBody JobRequirementRequest request) { return toResponse(careerService.updateRequirement(jobId, id, currentUserId, request)); }
    @DeleteMapping("/{jobId}/requirements/{id}") public ResponseEntity<Void> deleteRequirement(@PathVariable Long jobId, @PathVariable Long id, @CurrentUserId Long currentUserId) { careerService.deleteRequirement(jobId, id, currentUserId); return ResponseEntity.noContent().build(); }

    @PostMapping("/{jobId}/notes") public ResponseEntity<JobNoteResponse> createNote(@PathVariable Long jobId, @CurrentUserId Long currentUserId, @Valid @RequestBody JobNoteRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(careerService.createNote(jobId, currentUserId, request))); }
    @GetMapping("/{jobId}/notes") public List<JobNoteResponse> listNotes(@PathVariable Long jobId, @CurrentUserId Long currentUserId) { return careerService.listNotes(jobId, currentUserId).stream().map(this::toResponse).toList(); }
    @GetMapping("/{jobId}/notes/{id}") public JobNoteResponse getNote(@PathVariable Long jobId, @PathVariable Long id, @CurrentUserId Long currentUserId) { return toResponse(careerService.getNote(jobId, id, currentUserId)); }
    @PutMapping("/{jobId}/notes/{id}") public JobNoteResponse updateNote(@PathVariable Long jobId, @PathVariable Long id, @CurrentUserId Long currentUserId, @Valid @RequestBody JobNoteRequest request) { return toResponse(careerService.updateNote(jobId, id, currentUserId, request)); }
    @DeleteMapping("/{jobId}/notes/{id}") public ResponseEntity<Void> deleteNote(@PathVariable Long jobId, @PathVariable Long id, @CurrentUserId Long currentUserId) { careerService.deleteNote(jobId, id, currentUserId); return ResponseEntity.noContent().build(); }

    private JobResponse toResponse(Job value) { return new JobResponse(value.getId(), value.getCompanyId(), value.getTitle(), value.getCity(), value.getJobType(), value.getPublishDate(), value.getDeadline(), value.getRawJd(), value.getSourceType(), value.getSourceName(), value.getSourceUrl(), value.getArchived()); }
    private JobRequirementResponse toResponse(JobRequirement value) { return new JobRequirementResponse(value.getId(), value.getRequirementType(), value.getSkillId(), value.getRequirementText()); }
    private JobNoteResponse toResponse(JobNote value) { return new JobNoteResponse(value.getId(), value.getContent()); }
}
