package com.careerplatform.ai;

import com.careerplatform.ai.client.AiToolCallingGateway;
import com.careerplatform.ai.client.JobSearchGateway;
import com.careerplatform.ai.config.AiProperties;
import com.careerplatform.ai.config.JobDiscoveryConfiguration;
import com.careerplatform.ai.dto.job.JobDiscoveryAiResult;
import com.careerplatform.ai.dto.job.JobDiscoveryConfirmRequest;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import com.careerplatform.ai.service.JobSearchSession;
import com.careerplatform.ai.tool.JobSearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class JobDiscoveryConfigurationTest {
    @Test
    void disabledGatewaysStartWithoutSecretsAndExposeNoGlobalToolsOrModel() {
        new ApplicationContextRunner().withUserConfiguration(JobDiscoveryConfiguration.class)
                .withBean(AiProperties.class, AiProperties::new)
                .withBean(ObjectMapper.class, ObjectMapper::new).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatModel.class).doesNotHaveBean(ToolCallback.class)
                            .doesNotHaveBean(JobSearchTool.class);
                    var search = context.getBean(JobSearchGateway.class);
                    assertThatThrownBy(search::requireAvailable).isInstanceOf(AiServiceUnavailableException.class);
                    assertThatThrownBy(() -> context.getBean(AiToolCallingGateway.class)
                            .discover("system", "user", new JobSearchTool(search, new JobSearchSession())))
                            .isInstanceOf(AiServiceUnavailableException.class);
                });
    }

    @Test
    void modelAndConfirmationContractsCannotSupplySourceFactsOrTrustedIds() {
        String schema = new BeanOutputConverter<>(JobDiscoveryAiResult.class).getJsonSchema();
        assertThat(schema).contains("resultKey", "matchedSkillKeys", "fitSummary")
                .doesNotContain("sourceUrl", "sourceHost", "publishedAt", "companyId", "userId", "jobId", "model");
        assertThat(Arrays.stream(JobDiscoveryConfirmRequest.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("candidateId", "companyId", "title", "city", "jobType", "rawJd");
    }
}
