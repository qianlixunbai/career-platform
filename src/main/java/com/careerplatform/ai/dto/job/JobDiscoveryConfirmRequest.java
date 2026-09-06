package com.careerplatform.ai.dto.job;

import com.careerplatform.career.enums.JobType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Explicit user choices required to turn an ephemeral candidate into a Job. */
public record JobDiscoveryConfirmRequest(
        @NotBlank(message = "候选岗位不能为空") @Size(max = 100, message = "候选岗位标识无效") String candidateId,
        @NotNull(message = "公司不能为空") Long companyId,
        @NotBlank(message = "岗位名称不能为空") @Size(max = 150, message = "岗位名称长度不能超过150个字符") String title,
        @Size(max = 100, message = "城市长度不能超过100个字符") String city,
        @NotNull(message = "岗位类型不能为空") JobType jobType,
        @Size(max = 16_000, message = "岗位描述长度不能超过16000个字符") String rawJd) {
}
