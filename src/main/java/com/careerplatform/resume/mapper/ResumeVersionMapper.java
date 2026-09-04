package com.careerplatform.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careerplatform.resume.entity.ResumeVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeVersionMapper extends BaseMapper<ResumeVersion> {
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM resume_version "
            + "WHERE resume_id = #{resumeId}")
    Integer selectMaxVersionNo(@Param("resumeId") Long resumeId);

    @Select("SELECT id, user_id, resume_id, version_no, label, status, finalized_at, created_at, updated_at "
            + "FROM resume_version "
            + "WHERE id = #{versionId} AND resume_id = #{resumeId} AND user_id = #{userId} "
            + "FOR UPDATE")
    ResumeVersion selectOwnedInResumeForUpdate(@Param("versionId") Long versionId,
                                                @Param("resumeId") Long resumeId,
                                                @Param("userId") Long userId);
}
