package com.meetple.backend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.ErrorStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ERROR_STATUS_ATTRIBUTE = "authErrorStatus";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        ErrorStatus errorStatus = resolveErrorStatus(request);

        response.setStatus(errorStatus.getStatusCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorStatus).getBody());
    }

    private ErrorStatus resolveErrorStatus(HttpServletRequest request) {
        Object errorStatus = request.getAttribute(ERROR_STATUS_ATTRIBUTE);
        if (errorStatus instanceof ErrorStatus resolvedErrorStatus) {
            return resolvedErrorStatus;
        }
        return ErrorStatus.UNAUTHORIZED;
    }
}
