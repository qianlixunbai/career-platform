package com.careerplatform.profile.dto;

import com.careerplatform.profile.enums.CertificateAwardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class CertificateAwardRequest {
    @NotBlank(message = "名称不能为空") @Size(max = 200, message = "名称长度不能超过200个字符") private String name;
    @NotNull(message = "类型不能为空") private CertificateAwardType type;
    @NotBlank(message = "颁发方不能为空") @Size(max = 200, message = "颁发方长度不能超过200个字符") private String issuer;
    @NotNull(message = "颁发日期不能为空") private LocalDate issueDate;
    @Size(max = 5000, message = "描述长度不能超过5000个字符") private String description;
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public CertificateAwardType getType() { return type; } public void setType(CertificateAwardType type) { this.type = type; }
    public String getIssuer() { return issuer; } public void setIssuer(String issuer) { this.issuer = issuer; }
    public LocalDate getIssueDate() { return issueDate; } public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
}
