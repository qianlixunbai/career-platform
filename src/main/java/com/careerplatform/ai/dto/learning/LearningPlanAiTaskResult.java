package com.careerplatform.ai.dto.learning;

import java.time.LocalDate;
import java.util.List;

/** Untrusted structured task emitted by the provider before Java validation. */
public class LearningPlanAiTaskResult {
    private String title;
    private String description;
    private Integer plannedMinutes;
    private LocalDate dueDate;
    private Integer sortOrder;
    private List<String> evidenceKeys;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPlannedMinutes() {
        return plannedMinutes;
    }

    public void setPlannedMinutes(Integer plannedMinutes) {
        this.plannedMinutes = plannedMinutes;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public List<String> getEvidenceKeys() {
        return evidenceKeys;
    }

    public void setEvidenceKeys(List<String> evidenceKeys) {
        this.evidenceKeys = evidenceKeys;
    }
}
