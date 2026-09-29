package com.meetple.backend.domain.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import com.meetple.backend.global.exception.BaseException;

class AiSearchHttpTest {
    @Test void sendsScopedCapabilityAndParsesPythonContract() {
        var capability = new AiSearchCapability(AiSearchCapabilityTest.PROPERTIES);
        var repository = mock(AiMeetingSearchRepository.class);
        var builder = RestClient.builder().requestInterceptor((request, body, execution) -> {
            assertThat(request.getURI().getPath()).isEqualTo("/v1/search");
            assertThat(request.getHeaders().getFirst("Authorization")).isNull();
            assertThat(capability.verify(request.getHeaders().getFirst("X-AI-Service-Token"),
                    request.getHeaders().getFirst("X-Meetple-Capability"))).isEqualTo(42);
            assertThat(new String(body, StandardCharsets.UTF_8))
                    .contains("\"query\":\"러닝\"", "\"referenceTime\":\"")
                    .doesNotContain("memberId", "email");
            var response = new MockClientHttpResponse("""
                    {"status":"NEEDS_CLARIFICATION","message":"위치를 선택해주세요.",
                     "filters":null,"recommendations":[],"retrievalMode":"keyword"}
                    """.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
        });
        var service = new AiSearchService(AiSearchCapabilityTest.PROPERTIES, capability, repository, builder);
        var result = service.search(42, new AiSearchContracts.Request(" 러닝 ", null, null, 3000));
        assertThat(result.status()).isEqualTo(AiSearchContracts.Status.NEEDS_CLARIFICATION);
        verifyNoInteractions(repository);
    }

    @Test void upstreamFailuresBecomeServiceUnavailableWithoutLeakingBody() {
        var builder = RestClient.builder().requestInterceptor((request, body, execution) ->
                new MockClientHttpResponse("private upstream body".getBytes(StandardCharsets.UTF_8),
                        HttpStatus.TOO_MANY_REQUESTS));
        var service = new AiSearchService(AiSearchCapabilityTest.PROPERTIES,
                new AiSearchCapability(AiSearchCapabilityTest.PROPERTIES),
                mock(AiMeetingSearchRepository.class), builder);
        assertThatThrownBy(() -> service.search(42, new AiSearchContracts.Request("러닝", null, null, 3000)))
                .isInstanceOf(BaseException.class).hasMessageNotContaining("private upstream body");
    }
}
