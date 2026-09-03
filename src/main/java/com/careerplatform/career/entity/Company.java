package com.careerplatform.career.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("company")
public class Company {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String name;
    private String industry;
    private String city;
    private String website;
    private String size;
    private String notes;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getIndustry() { return industry; } public void setIndustry(String industry) { this.industry = industry; }
    public String getCity() { return city; } public void setCity(String city) { this.city = city; }
    public String getWebsite() { return website; } public void setWebsite(String website) { this.website = website; }
    public String getSize() { return size; } public void setSize(String size) { this.size = size; }
    public String getNotes() { return notes; } public void setNotes(String notes) { this.notes = notes; }
}
