package com.meetple.backend.domain.ai;

import java.net.http.HttpClient;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MeetingEmbeddingClient {
    private final AiSearchProperties searchProperties;
    private final RestClient client;

    public MeetingEmbeddingClient(
            AiSearchProperties searchProperties,
            AiEmbeddingProperties embeddingProperties,
            RestClient.Builder builder
    ) {
        this.searchProperties = searchProperties;
        if (embeddingProperties.enabled()
                && (searchProperties.serviceToken() == null
                || searchProperties.serviceToken().length() < 32)) {
            throw new IllegalArgumentException("모임 임베딩에는 32자 이상의 AI 서비스 키가 필요합니다.");
        }
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(3)).build());
        factory.setReadTimeout(searchProperties.timeout());
        this.client = builder.requestFactory(factory)
                .baseUrl(searchProperties.baseUrl().toString())
                .build();
    }

    public Response create(String document) {
        try {
            return client.post()
                    .uri("/v1/embeddings/meetings")
                    .header("X-AI-Service-Token", searchProperties.serviceToken())
                    .body(new Request(document))
                    .retrieve()
                    .body(Response.class);
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
                throw new NonRetryableMeetingEmbeddingException(
                        "AI 서버가 모임 임베딩 요청을 거부했습니다.", exception
                );
            }
            throw new MeetingEmbeddingProcessingException("AI 임베딩 요청 한도를 초과했습니다.", exception);
        } catch (RestClientException exception) {
            throw new MeetingEmbeddingProcessingException("AI 임베딩 서버를 호출할 수 없습니다.", exception);
        }
    }

    record Request(String document) {
    }

    public record Response(String embeddingModel, List<Double> embedding) {
    }
}
