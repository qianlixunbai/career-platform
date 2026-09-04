package com.careerplatform.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careerplatform.application.entity.Application;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ApplicationMapper extends BaseMapper<Application> {
    @Select("SELECT id, user_id, job_id, resume_version_id, current_stage, end_reason, end_note, "
            + "job_title_snapshot, company_name_snapshot, location_snapshot, job_description_snapshot, "
            + "applied_at, ended_at, created_at, updated_at FROM application "
            + "WHERE id = #{applicationId} AND user_id = #{userId} FOR UPDATE")
    Application selectOwnedForUpdate(@Param("applicationId") Long applicationId,
                                     @Param("userId") Long userId);
}
