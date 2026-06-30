package com.meetple.backend.domain.location.client;

import com.meetple.backend.domain.location.config.NaverLocationProperties;
import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

@Component
public class NaverLocationClient implements LocationClient {

    private static final String LOCAL_SEARCH_PATH = "/v1/search/local.json";
    private static final String NAVER_PROVIDER = "NAVER";
    private static final BigDecimal COORDINATE_SCALE = BigDecimal.valueOf(10_000_000L);

    private final RestClient restClient;
    private final NaverLocationProperties properties;

    public NaverLocationClient(RestClient.Builder restClientBuilder, NaverLocationProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public List<LocationSearchResponse> search(String query, int display) {
        validateConfiguration();

        try {
            NaverLocalSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(LOCAL_SEARCH_PATH)
                            .queryParam("query", query)
                            .queryParam("display", display)
                            .queryParam("start", 1)
                            .queryParam("sort", "random")
                            .build())
                    .header("X-Naver-Client-Id", properties.clientId())
                    .header("X-Naver-Client-Secret", properties.clientSecret())
                    .retrieve()
                    .body(NaverLocalSearchResponse.class);

            if (response == null || response.items() == null) {
                return List.of();
            }

            return response.items()
                    .stream()
                    .map(this::toResponse)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (RestClientException e) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 검색 호출에 실패했습니다.");
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.clientId()) || !StringUtils.hasText(properties.clientSecret())) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 검색 설정이 누락되었습니다.");
        }
    }

    private Optional<LocationSearchResponse> toResponse(NaverLocalSearchItem item) {
        Optional<Double> longitude = normalizeLongitude(item.mapx());
        Optional<Double> latitude = normalizeLatitude(item.mapy());
        if (longitude.isEmpty() || latitude.isEmpty() || !isKoreaCoordinate(latitude.get(), longitude.get())) {
            return Optional.empty();
        }

        String name = sanitize(item.title());
        String roadAddress = sanitize(item.roadAddress());
        String address = StringUtils.hasText(roadAddress) ? roadAddress : sanitize(item.address());

        return Optional.of(new LocationSearchResponse(
                createId(name, address, latitude.get(), longitude.get()),
                name,
                sanitize(item.category()),
                address,
                latitude.get(),
                longitude.get(),
                NAVER_PROVIDER
        ));
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return HtmlUtils.htmlUnescape(value.replaceAll("<[^>]*>", "")).trim();
    }

    private Optional<Double> normalizeLatitude(String value) {
        return normalizeCoordinate(value, 90.0);
    }

    private Optional<Double> normalizeLongitude(String value) {
        return normalizeCoordinate(value, 180.0);
    }

    private Optional<Double> normalizeCoordinate(String value, double maxAbsoluteValue) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }

        try {
            BigDecimal raw = new BigDecimal(value.trim());
            if (isWithin(raw.doubleValue(), maxAbsoluteValue)) {
                return Optional.of(raw.doubleValue());
            }

            BigDecimal scaled = raw.divide(COORDINATE_SCALE);
            if (isWithin(scaled.doubleValue(), maxAbsoluteValue)) {
                return Optional.of(scaled.doubleValue());
            }
            return Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private boolean isWithin(double value, double maxAbsoluteValue) {
        return Math.abs(value) <= maxAbsoluteValue;
    }

    private boolean isKoreaCoordinate(double latitude, double longitude) {
        return latitude >= 30.0
                && latitude <= 45.0
                && longitude >= 120.0
                && longitude <= 135.0;
    }

    private String createId(String name, String address, double latitude, double longitude) {
        String source = String.join("|", name, address, Double.toString(latitude), Double.toString(longitude));
        return "naver:" + Integer.toUnsignedString(Objects.hash(source));
    }

    private record NaverLocalSearchResponse(
            List<NaverLocalSearchItem> items
    ) {
    }

    private record NaverLocalSearchItem(
            String title,
            String category,
            String address,
            String roadAddress,
            String mapx,
            String mapy
    ) {
    }
}
