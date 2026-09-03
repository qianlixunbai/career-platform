package com.careerplatform.common.exception;

import com.careerplatform.user.exception.UsernameAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @Test
    void shouldMapDuplicateUsernameToConflict() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DuplicateUsernameController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/duplicate").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("用户名已存在"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    private static class DuplicateUsernameController {

        @PostMapping("/duplicate")
        void duplicate() {
            throw new UsernameAlreadyExistsException("用户名已存在");
        }
    }
}
