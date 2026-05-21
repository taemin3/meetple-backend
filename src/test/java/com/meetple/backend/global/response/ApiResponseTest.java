package com.meetple.backend.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ApiResponseTest {

    @Test
    void successIncludesCommonFields() {
        ResponseEntity<ApiResponse<String>> response = ApiResponse.success(SuccessStatus.OK, "DATA");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(200);
        assertThat(response.getBody().getSuccess()).isTrue();
        assertThat(response.getBody().getCode()).isEqualTo(20000);
        assertThat(response.getBody().getMessage()).isEqualTo(SuccessStatus.OK.getMessage());
        assertThat(response.getBody().getData()).isEqualTo("DATA");
    }

    @Test
    void successOnlyIncludesCommonFields() {
        ResponseEntity<ApiResponse<Void>> response = ApiResponse.successOnly(SuccessStatus.CREATED);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(201);
        assertThat(response.getBody().getSuccess()).isTrue();
        assertThat(response.getBody().getCode()).isEqualTo(20100);
        assertThat(response.getBody().getMessage()).isEqualTo(SuccessStatus.CREATED.getMessage());
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void errorIncludesCommonFields() {
        ResponseEntity<ApiResponse<Void>> response = ApiResponse.error(ErrorStatus.BAD_REQUEST);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(10001);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorStatus.BAD_REQUEST.getMessage());
        assertThat(response.getBody().getData()).isNull();
    }
}
