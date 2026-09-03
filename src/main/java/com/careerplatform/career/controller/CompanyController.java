package com.careerplatform.career.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.career.dto.CompanyRequest;
import com.careerplatform.career.dto.CompanyResponse;
import com.careerplatform.career.entity.Company;
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
@RequestMapping("/api/v1/companies")
public class CompanyController {
    private final CareerService careerService;
    public CompanyController(CareerService careerService) { this.careerService = careerService; }
    @PostMapping public ResponseEntity<CompanyResponse> create(@CurrentUserId Long currentUserId, @Valid @RequestBody CompanyRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(careerService.createCompany(currentUserId, request))); }
    @GetMapping public List<CompanyResponse> list(@CurrentUserId Long currentUserId) { return careerService.listCompanies(currentUserId).stream().map(this::toResponse).toList(); }
    @GetMapping("/{id}") public CompanyResponse get(@PathVariable Long id, @CurrentUserId Long currentUserId) { return toResponse(careerService.getCompany(id, currentUserId)); }
    @PutMapping("/{id}") public CompanyResponse update(@PathVariable Long id, @CurrentUserId Long currentUserId, @Valid @RequestBody CompanyRequest request) { return toResponse(careerService.updateCompany(id, currentUserId, request)); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id, @CurrentUserId Long currentUserId) { careerService.deleteCompany(id, currentUserId); return ResponseEntity.noContent().build(); }
    private CompanyResponse toResponse(Company value) { return new CompanyResponse(value.getId(), value.getName(), value.getIndustry(), value.getCity(), value.getWebsite(), value.getSize(), value.getNotes()); }
}
