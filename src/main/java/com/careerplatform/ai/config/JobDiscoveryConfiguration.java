package com.careerplatform.ai.config;

import com.careerplatform.ai.client.AiToolCallingGateway;
import com.careerplatform.ai.client.JobSearchGateway;
import com.careerplatform.ai.client.SpringAiToolCallingGateway;
import com.careerplatform.ai.client.TavilyJobSearchGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** M6C owns its gateways; no model, callback or business tool is registered globally. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JobSearchProperties.class)
public class JobDiscoveryConfiguration {
    @Bean
    @ConditionalOnMissingBean(JobSearchGateway.class)
    JobSearchGateway jobSearchGateway(JobSearchProperties properties, ObjectMapper mapper) {
        return new TavilyJobSearchGateway(properties, mapper);
    }

    @Bean
    @ConditionalOnMissingBean(AiToolCallingGateway.class)
    AiToolCallingGateway aiToolCallingGateway(AiProperties properties) {
        return new SpringAiToolCallingGateway(properties);
    }
}
