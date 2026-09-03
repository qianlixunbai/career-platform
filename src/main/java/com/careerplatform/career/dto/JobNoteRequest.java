package com.careerplatform.career.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class JobNoteRequest {
    @NotBlank(message = "岗位笔记不能为空") @Size(max = 16000, message = "岗位笔记长度不能超过16000个字符") private String content;
    public String getContent() { return content; } public void setContent(String content) { this.content = content; }
}
