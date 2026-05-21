package com.meetple.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final Integer status;
    private final Boolean success;
    private final Integer code;
    private final String message;
    private final T data;

    public static <T> ResponseEntity<ApiResponse<T>> success(SuccessStatus successStatus, T data) {
        ApiResponse<T> response = ApiResponse.<T>builder()
                .status(successStatus.getStatusCode())
                .success(true)
                .code(successStatus.getCode())
                .message(successStatus.getMessage())
                .data(data)
                .build();
        return ResponseEntity.status(successStatus.getStatusCode()).body(response);
    }

    public static ResponseEntity<ApiResponse<Void>> successOnly(SuccessStatus successStatus) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .status(successStatus.getStatusCode())
                .success(true)
                .code(successStatus.getCode())
                .message(successStatus.getMessage())
                .build();
        return ResponseEntity.status(successStatus.getStatusCode()).body(response);
    }

    public static ResponseEntity<ApiResponse<Void>> error(ErrorStatus errorStatus) {
        return error(errorStatus, errorStatus.getMessage());
    }

    public static ResponseEntity<ApiResponse<Void>> error(ErrorStatus errorStatus, String message) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .status(errorStatus.getStatusCode())
                .success(false)
                .code(errorStatus.getCode())
                .message(message)
                .build();
        return ResponseEntity.status(errorStatus.getStatusCode()).body(response);
    }

    public static ResponseEntity<ApiResponse<Void>> error(int status, int code, String message) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .status(status)
                .success(false)
                .code(code)
                .message(message)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
