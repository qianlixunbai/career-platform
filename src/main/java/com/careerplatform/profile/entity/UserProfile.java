package com.careerplatform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_profile")
public class UserProfile {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String fullName;
    private String phone;
    private String email;
    private String avatarUrl;
    private String currentCity;
    private String personalWebsite;
    private String githubUrl;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getFullName() { return fullName; } public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; } public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }public void setEmail(String email) { this.email = email; }
    public String getAvatarUrl() { return avatarUrl; } public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getCurrentCity() { return currentCity; } public void setCurrentCity(String currentCity) { this.currentCity = currentCity; }
    public String getPersonalWebsite() { return personalWebsite; } public void setPersonalWebsite(String personalWebsite) { this.personalWebsite = personalWebsite; }
    public String getGithubUrl() { return githubUrl; } public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
}
