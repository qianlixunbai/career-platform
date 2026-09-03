package com.careerplatform.career.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompanyRequest {
    @NotBlank(message = "公司名称不能为空") @Size(max = 150, message = "公司名称长度不能超过150个字符") private String name;
    @Size(max = 100, message = "行业长度不能超过100个字符") private String industry;
    @Size(max = 100, message = "城市长度不能超过100个字符") private String city;
    @Size(max = 255, message = "官网长度不能超过255个字符") private String website;
    @Size(max = 50, message = "公司规模长度不能超过50个字符") private String size;
    @Size(max = 16000, message = "备注长度不能超过16000个字符") private String notes;
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getIndustry() { return industry; } public void setIndustry(String industry) { this.industry = industry; }
    public String getCity() { return city; } public void setCity(String city) { this.city = city; }
    public String getWebsite() { return website; } public void setWebsite(String website) { this.website = website; }
    public String getSize() { return size; } public void setSize(String size) { this.size = size; }
    public String getNotes() { return notes; } public void setNotes(String notes) { this.notes = notes; }
}
