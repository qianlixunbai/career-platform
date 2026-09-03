package com.careerplatform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.profile.enums.Proficiency;

@TableName("user_skill")
public class UserSkill {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private Long skillId; private Proficiency proficiency;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getSkillId() { return skillId; } public void setSkillId(Long skillId) { this.skillId = skillId; }
    public Proficiency getProficiency() { return proficiency; } public void setProficiency(Proficiency proficiency) { this.proficiency = proficiency; }
}
