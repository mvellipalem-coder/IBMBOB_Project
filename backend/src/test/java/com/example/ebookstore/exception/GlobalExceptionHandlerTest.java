package com.example.ebookstore.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.config.SecurityConfig;
import com.example.ebookstore.security.RestAuthenticationEntryPoint;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Checks that every kind of failure becomes the standard error body, using a tiny throw-away controller.
 * No database is needed.
 */
@WebMvcTest
@Import({GlobalExceptionHandlerTest.ThrowingController.class, SecurityConfig.class,
        RestAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origin=http://localhost:5173")
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiExceptionUsesTheStatusAndCodeOfItsErrorCode() throws Exception {
        mockMvc.perform(get("/api/test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Book not found."))
            .andExpect(jsonPath("$.path").value("/api/test/not-found"));
    }

    @Test
    void invalidBodyReturnsFieldErrorsWithoutEchoingTheRejectedValue() throws Exception {
        mockMvc.perform(post("/api/test/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void malformedJsonReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/test/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void wrongPathVariableTypeReturnsValidationFailed() throws Exception {
        mockMvc.perform(get("/api/test/number/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(post("/api/test/not-found"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownUrlReturns404() throws Exception {
        mockMvc.perform(get("/api/test/does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unexpectedErrorsAreHiddenBehindAGenericMessage() throws Exception {
        mockMvc.perform(get("/api/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }

    /** Test-only endpoints that fail in different ways. */
    @RestController
    static class ThrowingController {

        record NameRequest(@NotBlank String name) { }

        @GetMapping("/api/test/not-found")
        String notFound() {
            throw new ApiException(ErrorCode.BOOK_NOT_FOUND);
        }

        @PostMapping("/api/test/validate")
        String validate(@Valid @RequestBody NameRequest request) {
            return request.name();
        }

        @GetMapping("/api/test/number/{id}")
        String number(@PathVariable("id") long id) {
            return String.valueOf(id);
        }

        @GetMapping("/api/test/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
