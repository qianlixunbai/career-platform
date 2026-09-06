package com.careerplatform.ai.client;

import com.careerplatform.ai.dto.job.JobDiscoveryAiResult;
import com.careerplatform.ai.tool.JobSearchTool;

/** Provider-neutral M6C boundary for one request-local search tool. */
public interface AiToolCallingGateway {

    /**
     * Discover job advice using only the explicitly supplied request-local
     * tool. The returned DTO contains model advice and opaque result keys;
     * provider source facts are resolved by the caller's session.
     */
    JobDiscoveryAiResult discover(String systemInstruction, String userContent, JobSearchTool tool);
}
