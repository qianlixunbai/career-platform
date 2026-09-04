package com.careerplatform.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careerplatform.resume.entity.Resume;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeMapper extends BaseMapper<Resume> {
    @Select("SELECT id, user_id, name, description, created_at, updated_at "
            + "FROM resume "
            + "WHERE id = #{resumeId} AND user_id = #{userId} "
            + "FOR UPDATE")
    Resume selectOwnedForUpdate(@Param("resumeId") Long resumeId, @Param("userId") Long userId);
}
