package com.careerplatform.career.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("job_note")
public class JobNote {
    @TableId(type = IdType.AUTO) private Long id;
    private Long jobId;
    private String content;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getJobId() { return jobId; } public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getContent() { return content; } public void setContent(String content) { this.content = content; }
}
