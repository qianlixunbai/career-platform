package com.careerplatform.career.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careerplatform.career.entity.Job;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface JobMapper extends BaseMapper<Job> {
    @Select("SELECT id, user_id, company_id, title, city, job_type, publish_date, deadline, raw_jd, "
            + "source_type, source_name, source_url, archived FROM job "
            + "WHERE id = #{jobId} AND user_id = #{userId} FOR UPDATE")
    Job selectOwnedForUpdate(@Param("jobId") Long jobId, @Param("userId") Long userId);
}
