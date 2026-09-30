package com.meetple.backend.domain.ai;

import static com.meetple.backend.domain.ai.AiSearchContracts.*;

import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AiSearchService {
    private final AiSearchProperties properties;
    private final AiSearchCapability capability;
    private final AiMeetingSearchRepository repository;
    private final RestClient client;

    public AiSearchService(AiSearchProperties properties, AiSearchCapability capability,
                           AiMeetingSearchRepository repository, RestClient.Builder builder) {
        this.properties = properties; this.capability = capability; this.repository = repository;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(3)).build());
        factory.setReadTimeout(properties.timeout());
        this.client = builder.requestFactory(factory).baseUrl(properties.baseUrl().toString()).build();
    }

    public Response search(long memberId, Request request) {
        capability.requireEnabled();
        Response response;
        try {
            response = client.post().uri("/v1/search")
                    .header("X-AI-Service-Token", properties.serviceToken())
                    .header("X-Meetple-Capability", capability.issue(memberId))
                    .body(new InternalRequest(request.query().strip(), request.latitude(), request.longitude(),
                            request.radiusMeters(), LocalDateTime.now(ZoneId.of("Asia/Seoul"))))
                    .retrieve().body(Response.class);
        } catch (RestClientException ex) {
            // 응답 본문과 HTTP 헤더에 질문 또는 자격증명이 포함될 수 있으므로 로그에 남기지 않는다.
            throw new BaseException(ErrorStatus.AI_SEARCH_UNAVAILABLE);
        }
        validateResponse(memberId, request, response);
        return response;
    }

    void validateResponse(long memberId, Request request, Response response) {
        if (response == null || response.status() == null || response.message() == null
                || response.message().length() > 500 || response.recommendations() == null
                || response.recommendations().size() > 5 || !"keyword".equals(response.retrievalMode())) throw invalid();
        if (response.status() == Status.INPUT_REQUIRED || response.status() == Status.UNSUPPORTED) {
            if (!response.recommendations().isEmpty() || response.filters() != null) throw invalid();
            return;
        }
        Filters f = response.filters();
        try { AiSearchToolController.validateFilters(f); } catch (RuntimeException ex) { throw invalid(); }
        if (request.latitude() == null || request.longitude() == null
                || Double.compare(f.latitude(), request.latitude()) != 0
                || Double.compare(f.longitude(), request.longitude()) != 0
                || f.radiusMeters() > request.radiusMeters()) throw invalid();
        if (response.status() == Status.NO_RESULTS) {
            if (!response.recommendations().isEmpty()) throw invalid();
            return;
        }
        if (response.recommendations().isEmpty()) throw invalid();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        if (!f.endsBefore().isAfter(now)) throw invalid();
        Filters currentFilters = new Filters(f.keyword(), f.category(),
                f.startsAt().isBefore(now) ? now : f.startsAt(), f.endsBefore(),
                f.startsAtTime(), f.endsBeforeTime(),
                f.latitude(), f.longitude(), f.radiusMeters());
        var current = repository.search(memberId, currentFilters).items();
        var seen = new HashSet<Long>();
        for (Recommendation recommendation : response.recommendations()) {
            if (recommendation == null || !seen.add(recommendation.meetingId())
                    || recommendation.evidenceQuote() == null || recommendation.evidenceQuote().isBlank()
                    || recommendation.evidenceQuote().length() > 500) throw invalid();
            boolean grounded = current.stream().anyMatch(m -> m.id() == recommendation.meetingId()
                    && (m.title().contains(recommendation.evidenceQuote()) || m.description().contains(recommendation.evidenceQuote())));
            if (!grounded) throw invalid();
        }
    }

    private BaseException invalid() { return new BaseException(ErrorStatus.AI_SEARCH_INVALID_RESPONSE); }
}
