package com.meetple.backend.domain.location.client;

import com.meetple.backend.domain.location.config.NaverLocationProperties;
import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.ErrorStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

@Component
@Slf4j
public class NaverLocationClient implements LocationClient {

    private static final String LOCAL_SEARCH_PATH = "/v1/search/local.json";
    private static final String GEOCODE_PATH = "/map-geocode/v2/geocode";
    private static final String REVERSE_GEOCODE_PATH = "/map-reversegeocode/v2/gc";
    private static final String NAVER_PROVIDER = "NAVER";
    private static final String PLACE_TYPE = "PLACE";
    private static final String ADDRESS_TYPE = "ADDRESS";
    private static final String REVERSE_ROAD_ADDRESS = "roadaddr";
    private static final int REVERSE_STATUS_OK = 0;
    private static final int REVERSE_STATUS_NO_RESULTS = 3;
    private static final String MOUNTAIN_LAND_TYPE = "2";
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

    @Override
    public LocationSearchResponse reverse(double latitude, double longitude) {
        validateMapsConfiguration();

        NaverReverseGeocodeResponse response;
        try {
            response = requestReverseGeocode(latitude, longitude);
        } catch (RestClientException e) {
            log.warn("Failed to call Naver Reverse Geocoding API.", e);
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 역조회 호출에 실패했습니다.");
        }

        if (response == null || response.results() == null) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 역조회 응답이 올바르지 않습니다.");
        }
        if (isReverseStatusNoResults(response.status())) {
            throw new NotFoundException("좌표에 해당하는 주소를 찾을 수 없습니다.");
        }
        if (!isReverseStatusOk(response.status())) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 역조회 응답이 올바르지 않습니다.");
        }

        return response.results()
                .stream()
                .map(result -> toReverseResponse(result, latitude, longitude))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("좌표에 해당하는 주소를 찾을 수 없습니다."));
    }

    private List<LocationSearchResponse> searchAddresses(String query, int display) {
        try {
            NaverGeocodeResponse response = requestGeocode(query);
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
            log.warn("Failed to call Naver Geocoding API.", e);
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
            log.warn("Failed to call Naver Local Search API.", e);
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 검색 호출에 실패했습니다.");
        }
    }

    private NaverGeocodeResponse requestGeocode(String query) {
        return restClient.get()
                .uri(properties.mapsBaseUrl() + GEOCODE_PATH + "?query={query}", query)
                .header("X-NCP-APIGW-API-KEY-ID", properties.mapsClientId())
                .header("X-NCP-APIGW-API-KEY", properties.mapsClientSecret())
                .retrieve()
                .body(NaverGeocodeResponse.class);
    }

    private NaverReverseGeocodeResponse requestReverseGeocode(double latitude, double longitude) {
        return restClient.get()
                .uri(properties.mapsBaseUrl() + REVERSE_GEOCODE_PATH
                                + "?coords={coords}&orders=roadaddr,addr&output=json",
                        longitude + "," + latitude
                )
                .header("X-NCP-APIGW-API-KEY-ID", properties.mapsClientId())
                .header("X-NCP-APIGW-API-KEY", properties.mapsClientSecret())
                .retrieve()
                .body(NaverReverseGeocodeResponse.class);
    }

    private void validateAnyConfiguration() {
        if (!hasSearchConfiguration() && !hasMapsConfiguration()) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 위치 검색 설정이 누락되었습니다.");
        }
    }

    private void validateMapsConfiguration() {
        if (!hasMapsConfiguration()) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "네이버 지도 API 설정이 누락되었습니다.");
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
        Optional<Coordinate> coordinate = normalizeCoordinatePair(address.y(), address.x());
        if (coordinate.isEmpty()) {
            return Optional.empty();
        }

        String roadAddress = sanitize(address.roadAddress());
        String jibunAddress = sanitize(address.jibunAddress());
        String displayAddress = StringUtils.hasText(roadAddress) ? roadAddress : jibunAddress;
        if (!StringUtils.hasText(displayAddress)) {
            return Optional.empty();
        }

        return Optional.of(new LocationSearchResponse(
                createId(
                        ADDRESS_TYPE,
                        displayAddress,
                        displayAddress,
                        coordinate.get().latitude(),
                        coordinate.get().longitude()
                ),
                ADDRESS_TYPE,
                displayAddress,
                "주소",
                displayAddress,
                coordinate.get().latitude(),
                coordinate.get().longitude(),
                NAVER_PROVIDER
        ));
    }

    private Optional<LocationSearchResponse> toPlaceResponse(NaverLocalSearchItem item) {
        String name = sanitize(item.title());
        String roadAddress = sanitize(item.roadAddress());
        String address = StringUtils.hasText(roadAddress) ? roadAddress : sanitize(item.address());
        Optional<Coordinate> coordinate = normalizeCoordinatePair(item.mapy(), item.mapx())
                .or(() -> findAddressCoordinate(address));
        if (coordinate.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LocationSearchResponse(
                createId(PLACE_TYPE, name, address, coordinate.get().latitude(), coordinate.get().longitude()),
                PLACE_TYPE,
                name,
                sanitize(item.category()),
                address,
                coordinate.get().latitude(),
                coordinate.get().longitude(),
                NAVER_PROVIDER
        ));
    }

    private Optional<LocationSearchResponse> toReverseResponse(
            NaverReverseGeocodeResult result,
            double latitude,
            double longitude
    ) {
        String address = buildReverseAddress(result);
        if (!StringUtils.hasText(address)) {
            return Optional.empty();
        }

        String name = buildReverseName(result, address);
        return Optional.of(new LocationSearchResponse(
                createId(ADDRESS_TYPE, name, address, latitude, longitude),
                ADDRESS_TYPE,
                name,
                "주소",
                address,
                latitude,
                longitude,
                NAVER_PROVIDER
        ));
    }

    private String buildReverseAddress(NaverReverseGeocodeResult result) {
        if (REVERSE_ROAD_ADDRESS.equals(sanitize(result.name()))) {
            return joinNonBlank(
                    buildRoadRegionAddress(result.region()),
                    buildLandAddress(result.land(), false)
            );
        }

        return joinNonBlank(
                buildRegionAddress(result.region()),
                buildLandAddress(result.land(), true)
        );
    }

    private String buildReverseName(NaverReverseGeocodeResult result, String fallbackAddress) {
        if (result.land() != null) {
            String additionName = additionValue(result.land().addition0());
            if (StringUtils.hasText(additionName)) {
                return additionName;
            }
        }
        return fallbackAddress;
    }

    private String buildRegionAddress(NaverReverseRegion region) {
        if (region == null) {
            return "";
        }
        return joinNonBlank(
                areaName(region.area1()),
                areaName(region.area2()),
                areaName(region.area3()),
                areaName(region.area4())
        );
    }

    private String buildRoadRegionAddress(NaverReverseRegion region) {
        if (region == null) {
            return "";
        }
        return joinNonBlank(
                areaName(region.area1()),
                areaName(region.area2())
        );
    }

    private String buildLandAddress(NaverReverseLand land, boolean includeMountainPrefix) {
        if (land == null) {
            return "";
        }
        return joinNonBlank(
                land.name(),
                buildLandNumber(land.type(), land.number1(), land.number2(), includeMountainPrefix)
        );
    }

    private String buildLandNumber(
            String type,
            String number1,
            String number2,
            boolean includeMountainPrefix
    ) {
        String mainNumber = sanitize(number1);
        String subNumber = sanitize(number2);
        if (!StringUtils.hasText(mainNumber)) {
            return "";
        }
        if (includeMountainPrefix && MOUNTAIN_LAND_TYPE.equals(sanitize(type))) {
            mainNumber = "산" + mainNumber;
        }
        if (!StringUtils.hasText(subNumber) || "0".equals(subNumber)) {
            return mainNumber;
        }
        return mainNumber + "-" + subNumber;
    }

    private String areaName(NaverReverseArea area) {
        if (area == null) {
            return "";
        }
        return sanitize(area.name());
    }

    private String additionValue(NaverReverseAddition addition) {
        if (addition == null) {
            return "";
        }
        String value = sanitize(addition.value());
        if (StringUtils.hasText(value)) {
            return value;
        }
        return sanitize(addition.name());
    }

    private Optional<Coordinate> findAddressCoordinate(String address) {
        if (!hasMapsConfiguration() || !StringUtils.hasText(address)) {
            return Optional.empty();
        }

        try {
            NaverGeocodeResponse response = requestGeocode(address);
            if (response == null || response.addresses() == null) {
                return Optional.empty();
            }

            return response.addresses()
                    .stream()
                    .map(candidate -> normalizeCoordinatePair(candidate.y(), candidate.x()))
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (RestClientException e) {
            log.warn("Failed to geocode Naver Local Search address.", e);
            return Optional.empty();
        }
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

    private String joinNonBlank(String... values) {
        return Arrays.stream(values)
                .map(this::sanitize)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }

    private boolean isReverseStatusOk(NaverReverseStatus status) {
        return status != null && Objects.equals(status.code(), REVERSE_STATUS_OK);
    }

    private boolean isReverseStatusNoResults(NaverReverseStatus status) {
        return status != null && Objects.equals(status.code(), REVERSE_STATUS_NO_RESULTS);
    }

    private Optional<Double> normalizeLatitude(String value) {
        return normalizeCoordinate(value, 90.0);
    }

    private Optional<Double> normalizeLongitude(String value) {
        return normalizeCoordinate(value, 180.0);
    }

    private Optional<Coordinate> normalizeCoordinatePair(String latitudeValue, String longitudeValue) {
        Optional<Double> latitude = normalizeLatitude(latitudeValue);
        Optional<Double> longitude = normalizeLongitude(longitudeValue);
        if (latitude.isEmpty() || longitude.isEmpty() || !isKoreaCoordinate(latitude.get(), longitude.get())) {
            return Optional.empty();
        }
        return Optional.of(new Coordinate(latitude.get(), longitude.get()));
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

    private record NaverReverseGeocodeResponse(
            NaverReverseStatus status,
            List<NaverReverseGeocodeResult> results
    ) {
    }

    private record NaverReverseStatus(
            Integer code,
            String name,
            String message
    ) {
    }

    private record NaverReverseGeocodeResult(
            String name,
            NaverReverseRegion region,
            NaverReverseLand land
    ) {
    }

    private record NaverReverseRegion(
            NaverReverseArea area1,
            NaverReverseArea area2,
            NaverReverseArea area3,
            NaverReverseArea area4
    ) {
    }

    private record NaverReverseArea(
            String name
    ) {
    }

    private record NaverReverseLand(
            String name,
            String type,
            String number1,
            String number2,
            NaverReverseAddition addition0
    ) {
    }

    private record NaverReverseAddition(
            String name,
            String value
    ) {
    }

    private record Coordinate(
            double latitude,
            double longitude
    ) {
    }
}
