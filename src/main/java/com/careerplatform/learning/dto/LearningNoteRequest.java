package com.careerplatform.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class LearningNoteRequest {

    @Positive(message = "任务ID必须为正数")
    private Long taskId;

    @NotBlank(message = "笔记标题不能为空")
    @Size(max = 200, message = "笔记标题长度不能超过200个字符")
    private String title;

    @NotBlank(message = "笔记内容不能为空")
    @Size(max = 16_000, message = "笔记内容长度不能超过16000个字符")
    private String content;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
