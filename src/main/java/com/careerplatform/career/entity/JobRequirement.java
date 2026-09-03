package com.careerplatform.career.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.career.enums.RequirementType;

@TableName("job_requirement")
public class JobRequirement {
    @TableId(type = IdType.AUTO) private Long id;
    private Long jobId;
    private RequirementType requirementType;
    private Long skillId;
    private String requirementText;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getJobId() { return jobId; } public void setJobId(Long jobId) { this.jobId = jobId; }
    public RequirementType getRequirementType() { return requirementType; } public void setRequirementType(RequirementType requirementType) { this.requirementType = requirementType; }
    public Long getSkillId() { return skillId; } public void setSkillId(Long skillId) { this.skillId = skillId; }
    public String getRequirementText() { return requirementText; } public void setRequirementText(String requirementText) { this.requirementText = requirementText; }
}
