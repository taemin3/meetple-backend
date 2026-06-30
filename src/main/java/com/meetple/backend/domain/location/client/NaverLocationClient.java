package com.meetple.backend.domain.location.client;

import com.meetple.backend.domain.location.config.NaverLocationProperties;
import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String GEOCODE_PATH = "/map-geocode/v2/geocode";
    private static final String NAVER_PROVIDER = "NAVER";
    private static final String PLACE_TYPE = "PLACE";
    private static final String ADDRESS_TYPE = "ADDRESS";
    private static final BigDecimal COORDINATE_SCALE = BigDecimal.valueOf(10_000_000L);

    private final RestClient restClient;
    private final NaverLocationProperties properties;

    public NaverLocationClient(RestClient.Builder restClientBuilder, NaverLocationProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public List<LocationSearchResponse> search(String query, int display) {
        validateAnyConfiguration();

        List<LocationSearchResponse> responses = new ArrayList<>();
        if (hasMapsConfiguration()) {
            responses.addAll(searchAddresses(query, display));
        }
        if (hasSearchConfiguration()) {
            responses.addAll(searchPlaces(query, display));
        }

        return mergeAndLimit(responses, display);
    }

    private List<LocationSearchResponse> searchAddresses(String query, int display) {
        try {
            NaverGeocodeResponse response = restClient.get()
                    .uri(properties.mapsBaseUrl() + GEOCODE_PATH + "?query={query}", query)
                    .header("X-NCP-APIGW-API-KEY-ID", properties.mapsClientId())
                    .header("X-NCP-APIGW-API-KEY", properties.mapsClientSecret())
                    .retrieve()
                    .body(NaverGeocodeResponse.class);

            if (response == null || response.addresses() == null) {
                return List.of();
            }

            return response.addresses()
                    .stream()
                    .limit(display)
                    .map(this::toAddressResponse)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (RestClientException e) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 검색 호출에 실패했습니다.");
        }
    }

    private List<LocationSearchResponse> searchPlaces(String query, int display) {
        try {
            NaverLocalSearchResponse response = restClient.get()
                    .uri(properties.searchBaseUrl() + LOCAL_SEARCH_PATH
                                    + "?query={query}&display={display}&start=1&sort=random",
                            query,
                            display
                    )
                    .header("X-Naver-Client-Id", properties.searchClientId())
                    .header("X-Naver-Client-Secret", properties.searchClientSecret())
                    .retrieve()
                    .body(NaverLocalSearchResponse.class);

            if (response == null || response.items() == null) {
                return List.of();
            }

            return response.items()
                    .stream()
                    .map(this::toPlaceResponse)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (RestClientException e) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 검색 호출에 실패했습니다.");
        }
    }

    private void validateAnyConfiguration() {
        if (!hasSearchConfiguration() && !hasMapsConfiguration()) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 검색 설정이 누락되었습니다.");
        }
    }

    private boolean hasSearchConfiguration() {
        return StringUtils.hasText(properties.searchBaseUrl())
                && StringUtils.hasText(properties.searchClientId())
                && StringUtils.hasText(properties.searchClientSecret());
    }

    private boolean hasMapsConfiguration() {
        return StringUtils.hasText(properties.mapsBaseUrl())
                && StringUtils.hasText(properties.mapsClientId())
                && StringUtils.hasText(properties.mapsClientSecret());
    }

    private Optional<LocationSearchResponse> toAddressResponse(NaverGeocodeAddress address) {
        Optional<Double> longitude = normalizeLongitude(address.x());
        Optional<Double> latitude = normalizeLatitude(address.y());
        if (longitude.isEmpty() || latitude.isEmpty() || !isKoreaCoordinate(latitude.get(), longitude.get())) {
            return Optional.empty();
        }

        String roadAddress = sanitize(address.roadAddress());
        String jibunAddress = sanitize(address.jibunAddress());
        String displayAddress = StringUtils.hasText(roadAddress) ? roadAddress : jibunAddress;
        if (!StringUtils.hasText(displayAddress)) {
            return Optional.empty();
        }

        return Optional.of(new LocationSearchResponse(
                createId(ADDRESS_TYPE, displayAddress, displayAddress, latitude.get(), longitude.get()),
                ADDRESS_TYPE,
                displayAddress,
                "주소",
                displayAddress,
                latitude.get(),
                longitude.get(),
                NAVER_PROVIDER
        ));
    }

    private Optional<LocationSearchResponse> toPlaceResponse(NaverLocalSearchItem item) {
        Optional<Double> longitude = normalizeLongitude(item.mapx());
        Optional<Double> latitude = normalizeLatitude(item.mapy());
        if (longitude.isEmpty() || latitude.isEmpty() || !isKoreaCoordinate(latitude.get(), longitude.get())) {
            return Optional.empty();
        }

        String name = sanitize(item.title());
        String roadAddress = sanitize(item.roadAddress());
        String address = StringUtils.hasText(roadAddress) ? roadAddress : sanitize(item.address());

        return Optional.of(new LocationSearchResponse(
                createId(PLACE_TYPE, name, address, latitude.get(), longitude.get()),
                PLACE_TYPE,
                name,
                sanitize(item.category()),
                address,
                latitude.get(),
                longitude.get(),
                NAVER_PROVIDER
        ));
    }

    private List<LocationSearchResponse> mergeAndLimit(List<LocationSearchResponse> responses, int display) {
        Map<String, LocationSearchResponse> merged = new LinkedHashMap<>();
        for (LocationSearchResponse response : responses) {
            String key = response.address() + "|" + response.latitude() + "|" + response.longitude();
            merged.putIfAbsent(key, response);
        }
        return merged.values()
                .stream()
                .limit(display)
                .toList();
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

    private String createId(String type, String name, String address, double latitude, double longitude) {
        String source = String.join("|", type, name, address, Double.toString(latitude), Double.toString(longitude));
        return "naver:" + type.toLowerCase() + ":" + Integer.toUnsignedString(Objects.hash(source));
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

    private record NaverGeocodeResponse(
            List<NaverGeocodeAddress> addresses
    ) {
    }

    private record NaverGeocodeAddress(
            String roadAddress,
            String jibunAddress,
            String x,
            String y
    ) {
    }
}
