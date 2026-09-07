package com.careerplatform.ai.controller;

import com.careerplatform.ai.service.RagService;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.auth.CurrentUserIdArgumentResolver;
import com.careerplatform.auth.BearerTokenInterceptor;
import com.careerplatform.common.exception.GlobalExceptionHandler;
import com.careerplatform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RagControllerTest {
    RagService service = mock(RagService.class);
    MockMvc mvc;
    String base = "/api/v1/learning-plans/10/materials";
    @BeforeEach void init() {
        mvc = MockMvcBuilders.standaloneSetup(new RagController(service))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void missingAuthenticationRejected() throws Exception {
        mvc.perform(get(base+"/rag/status")).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void oversizedAndBlankQuestionRejectedBeforeService() throws Exception {
        for (String question : new String[]{"", "x".repeat(1001)})
            mvc.perform(post(base+"/rag/query").requestAttr(BearerTokenInterceptor.CURRENT_USER_ID_ATTRIBUTE,1L)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\""+question+"\"}"))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void sourceUsesAuthenticatedOwnerAndHidesForeignResource() throws Exception {
        when(service.source(10L,20L,30L,1L,null)).thenThrow(new ResourceNotFoundException("学习资料不存在"));
        mvc.perform(get(base+"/20/chunks/30").requestAttr(BearerTokenInterceptor.CURRENT_USER_ID_ATTRIBUTE,1L))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
    @Test void providerDetailsNotExposedByHandler() throws Exception {
        when(service.query(eq(10L),eq(1L),any())).thenThrow(new AiProviderException("synthetic-private-provider-body"));
        mvc.perform(post(base+"/rag/query").requestAttr(BearerTokenInterceptor.CURRENT_USER_ID_ATTRIBUTE,1L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Java?\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_PROVIDER_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-private"))));
    }
}
