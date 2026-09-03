package com.careerplatform.career.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.career.dto.CareerGoalRequest;
import com.careerplatform.career.dto.CareerGoalResponse;
import com.careerplatform.career.entity.CareerGoal;
import com.careerplatform.career.service.CareerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/career-goals")
public class CareerGoalController {
    private final CareerService careerService;
    public CareerGoalController(CareerService careerService) { this.careerService = careerService; }
    @PostMapping public ResponseEntity<CareerGoalResponse> create(@CurrentUserId Long currentUserId, @Valid @RequestBody CareerGoalRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(careerService.createGoal(currentUserId, request))); }
    @GetMapping public List<CareerGoalResponse> list(@CurrentUserId Long currentUserId) { return careerService.listGoals(currentUserId).stream().map(this::toResponse).toList(); }
    @GetMapping("/{id}") public CareerGoalResponse get(@PathVariable Long id, @CurrentUserId Long currentUserId) { return toResponse(careerService.getGoal(id, currentUserId)); }
    @PutMapping("/{id}") public CareerGoalResponse update(@PathVariable Long id, @CurrentUserId Long currentUserId, @Valid @RequestBody CareerGoalRequest request) { return toResponse(careerService.updateGoal(id, currentUserId, request)); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id, @CurrentUserId Long currentUserId) { careerService.deleteGoal(id, currentUserId); return ResponseEntity.noContent().build(); }
    private CareerGoalResponse toResponse(CareerGoal value) { return new CareerGoalResponse(value.getId(), value.getTargetPosition(), value.getTargetCity(), value.getTargetIndustry(), value.getTargetCompanyPreference(), value.getSalaryExpectation(), value.getNotes(), value.getStatus()); }
}
