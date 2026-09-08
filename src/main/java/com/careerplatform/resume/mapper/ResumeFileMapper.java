package com.careerplatform.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careerplatform.resume.entity.ResumeFile;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeFileMapper extends BaseMapper<ResumeFile> {

    @Select("SELECT id, user_id, resume_version_id, original_filename, content_type, file_size, "
            + "created_at, updated_at FROM resume_file "
            + "WHERE resume_version_id = #{versionId} AND user_id = #{userId}")
    ResumeFile selectMetadataByVersionAndUser(@Param("versionId") Long versionId,
                                               @Param("userId") Long userId);

    @Select("SELECT id, user_id, resume_version_id, original_filename, content_type, file_size, file_data, "
            + "created_at, updated_at FROM resume_file "
            + "WHERE resume_version_id = #{versionId} AND user_id = #{userId}")
    ResumeFile selectContentByVersionAndUser(@Param("versionId") Long versionId,
                                              @Param("userId") Long userId);
}
