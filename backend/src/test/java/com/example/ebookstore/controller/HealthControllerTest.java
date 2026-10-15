package com.example.ebookstore.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ebookstore.config.SecurityConfig;
import com.example.ebookstore.exception.GlobalExceptionHandler;
import com.example.ebookstore.security.RestAuthenticationEntryPoint;

/** Web-layer test: no database needed. Checks the public/protected rules and the 401 error body. */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origin=http://localhost:5173")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpointsReturnTheStandardUnauthorizedBody() throws Exception {
        mockMvc.perform(get("/api/cart"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.path").value("/api/cart"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
