package com.example.ebookstore.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.example.ebookstore.dto.ErrorResponse;
import com.example.ebookstore.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs when a request needs authentication but has no valid token.
 *
 * <p>Spring Security rejects such requests in a servlet filter, which is <em>before</em> any controller runs,
 * so {@code GlobalExceptionHandler} cannot see them. This class writes the same JSON error body
 * ({@code 401 UNAUTHORIZED}) that the OpenAPI contract promises.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorResponse body = ErrorResponse.of(ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.getDefaultMessage(), request.getRequestURI());
        response.setStatus(ErrorCode.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
