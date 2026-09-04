package com.careerplatform.resume.dto;

import com.careerplatform.resume.enums.ResumeSectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class ResumeContentItemRequest {

    @NotNull(message = "简历内容分区类型不能为空")
    private ResumeSectionType sectionType;

    @Size(max = 200, message = "简历内容标题长度不能超过200个字符")
    private String title;

    @NotBlank(message = "简历内容不能为空")
    @Size(max = 16_000, message = "简历内容长度不能超过16000个字符")
    private String content;

    @NotNull(message = "简历内容排序不能为空")
    @PositiveOrZero(message = "简历内容排序必须为非负数")
    private Integer sortOrder;

    public ResumeSectionType getSectionType() {
        return sectionType;
    }

    public void setSectionType(ResumeSectionType sectionType) {
        this.sectionType = sectionType;
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
