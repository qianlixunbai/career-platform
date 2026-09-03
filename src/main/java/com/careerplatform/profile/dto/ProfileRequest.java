package com.careerplatform.profile.dto;

import jakarta.validation.constraints.Size;

public class ProfileRequest {
    @Size(max = 100, message = "姓名长度不能超过100个字符") private String fullName;
    @Size(max = 50, message = "手机号长度不能超过50个字符") private String phone;
    @Size(max = 500, message = "头像地址长度不能超过500个字符") private String avatarUrl;
    @Size(max = 100, message = "当前城市长度不能超过100个字符") private String currentCity;
    @Size(max = 500, message = "个人网站长度不能超过500个字符") private String personalWebsite;
    @Size(max = 500, message = "GitHub 地址长度不能超过500个字符") private String githubUrl;
    public String getFullName() { return fullName; } public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; } public void setPhone(String phone) { this.phone = phone; }
    public String getAvatarUrl() { return avatarUrl; } public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getCurrentCity() { return currentCity; } public void setCurrentCity(String currentCity) { this.currentCity = currentCity; }
    public String getPersonalWebsite() { return personalWebsite; } public void setPersonalWebsite(String personalWebsite) { this.personalWebsite = personalWebsite; }
    public String getGithubUrl() { return githubUrl; } public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
}
