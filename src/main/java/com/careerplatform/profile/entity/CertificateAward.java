package com.careerplatform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.profile.enums.CertificateAwardType;
import java.time.LocalDate;

@TableName("certificate_award")
public class CertificateAward {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private String name; private CertificateAwardType type; private String issuer; private LocalDate issueDate; private String description;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public CertificateAwardType getType() { return type; } public void setType(CertificateAwardType type) { this.type = type; }
    public String getIssuer() { return issuer; } public void setIssuer(String issuer) { this.issuer = issuer; }
    public LocalDate getIssueDate() { return issueDate; } public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
}
