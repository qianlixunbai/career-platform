package com.careerplatform.ai.dto.job;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** User-controlled, bounded inputs for one ephemeral job discovery request. */
public record JobDiscoveryRequest(
        @NotNull(message = "职业目标不能为空") Long careerGoalId,
        @Size(max = 1_000, message = "搜索补充说明长度不能超过1000个字符") String searchNote,
        @Size(max = 100, message = "地点长度不能超过100个字符") String locationOverride,
        @Min(value = 1, message = "候选数量必须在1到10之间")
        @Max(value = 10, message = "候选数量必须在1到10之间") Integer maxCandidates) {
}
