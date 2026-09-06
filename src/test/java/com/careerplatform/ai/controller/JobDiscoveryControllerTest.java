package com.careerplatform.ai.controller;

import com.careerplatform.ai.dto.job.JobDiscoveryConfirmResponse;
import com.careerplatform.ai.dto.job.JobDiscoveryResponse;
import com.careerplatform.ai.service.JobDiscoveryService;
import com.careerplatform.auth.BearerTokenInterceptor;
import com.careerplatform.auth.CurrentUserIdArgumentResolver;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JobDiscoveryControllerTest {
    private static final Long USER_ID = 91L;

    @Mock
    private JobDiscoveryService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JobDiscoveryController controller = new JobDiscoveryController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void discoveryValidationIsAppliedAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/ai/discovery")
                        .requestAttr(BearerTokenInterceptor.CURRENT_USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careerGoalId\":1,\"maxCandidates\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedRequestsAreRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/ai/discovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careerGoalId\":1,\"maxCandidates\":3}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test
    void confirmValidationIsAppliedBeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/ai/discovery/confirm")
                        .requestAttr(BearerTokenInterceptor.CURRENT_USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"opaque-id\",\"companyId\":9,"
                                + "\"title\":\"\",\"jobType\":\"FULL_TIME\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void discoveryAndConfirmUseSeparateEndpointsAndConfirmReturns201() throws Exception {
        when(service.discover(eq(USER_ID), any()))
                .thenReturn(new JobDiscoveryResponse(List.of(), List.of("no reliable result"), 1));
        when(service.confirm(eq(USER_ID), any()))
                .thenReturn(new JobDiscoveryConfirmResponse(123L, List.of()));

        mockMvc.perform(post("/api/v1/jobs/ai/discovery")
                        .requestAttr(BearerTokenInterceptor.CURRENT_USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careerGoalId\":1,\"searchNote\":\"backend\",\"maxCandidates\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.searchCalls").value(1));

        mockMvc.perform(post("/api/v1/jobs/ai/discovery/confirm")
                        .requestAttr(BearerTokenInterceptor.CURRENT_USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"opaque-id\",\"companyId\":9,"
                                + "\"title\":\"Backend engineer\",\"city\":\"Shanghai\","
                                + "\"jobType\":\"FULL_TIME\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(123));

        verify(service).discover(eq(USER_ID), any());
        verify(service).confirm(eq(USER_ID), any());
    }

    @Test
    void aiInvalidResponseUsesSanitizedErrorContract() throws Exception {
        doThrow(new AiInvalidResponseException("internal result key detail"))
                .when(service).discover(eq(USER_ID), any());

        mockMvc.perform(post("/api/v1/jobs/ai/discovery")
                        .requestAttr(BearerTokenInterceptor.CURRENT_USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careerGoalId\":1,\"maxCandidates\":3}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"))
                .andExpect(jsonPath("$.message").value("AI 返回内容无法安全处理，请重试"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AiInvalidResponseException.Rule.class,
            names = {"MAX_RESULTS_TYPE_INVALID", "FINAL_RESPONSE_JSON_INVALID", "ADVICE_LIST_NULL", "UNKNOWN_RESULT_KEY",
                    "FINAL_RESPONSE_CLEANER_FAILED", "FINAL_RESPONSE_JSON_SYNTAX_INVALID",
                    "FINAL_RESPONSE_MAPPING_FAILED", "FINAL_RESPONSE_UNKNOWN_PROPERTY",
                    "FINAL_RESPONSE_DESERIALIZATION_FAILED"})
    @ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void classifiedFailuresKeepHttpContractAndLogOnlyEnums(AiInvalidResponseException.Rule rule,
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        String secret = "Authorization: Bearer synthetic-model-secret-8241";
        var failure = new AiInvalidResponseException(rule, secret);
        doThrow(failure).when(service).discover(eq(USER_ID), any());
        String body = mockMvc.perform(post("/api/v1/jobs/ai/discovery")
                        .requestAttr(BearerTokenInterceptor.CURRENT_USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careerGoalId\":1,\"maxCandidates\":3}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"))
                .andExpect(jsonPath("$.message").value("AI 返回内容无法安全处理，请重试"))
                .andExpect(jsonPath("$.stage").doesNotExist())
                .andExpect(jsonPath("$.ruleId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(secret, rule.name(), failure.getStage().name());
        org.assertj.core.api.Assertions.assertThat(output.getAll())
                .contains("AI_INVALID_RESPONSE stage=" + failure.getStage() + " rule=" + rule)
                .doesNotContain(secret);
        org.assertj.core.api.Assertions.assertThat(failure).hasNoCause();
    }
}
