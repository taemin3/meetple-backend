package com.meetple.backend.domain.location.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meetple.backend.domain.location.config.NaverLocationProperties;
import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.exception.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverLocationClientTest {

    private static final String ENCODED_YEOUIDO_PARK_QUERY =
            "%EC%97%AC%EC%9D%98%EB%8F%84%EA%B3%B5%EC%9B%90";
    private static final String ENCODED_YEOUIDO_PARK_ADDRESS =
            "%EC%84%9C%EC%9A%B8%20%EC%98%81%EB%93%B1%ED%8F%AC%EA%B5%AC%20%EC%97%AC%EC%9D%98%EA%B3%B5%EC%9B%90%EB%A1%9C%2068";
    private static final String ENCODED_FULL_YEOUIDO_PARK_ADDRESS =
            "%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84%EC%8B%9C%20%EC%98%81%EB%93%B1%ED%8F%AC%EA%B5%AC%20%EC%97%AC%EC%9D%98%EA%B3%B5%EC%9B%90%EB%A1%9C%2068";

    private MockRestServiceServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.verify();
        }
    }

    @Test
    void searchCombinesAddressAndPlaceResponses() {
        NaverLocationClient client = createClient(defaultProperties());
        expectGeocode("여의도공원", """
                {
                  "addresses": []
                }
                """);
        expectLocalSearch("여의도공원", """
                {
                  "items": [
                    {
                      "title": "<b>여의도공원</b>",
                      "category": "여행,명소&gt;공원",
                      "address": "서울특별시 영등포구 여의도동 2",
                      "roadAddress": "서울특별시 영등포구 여의공원로 68",
                      "mapx": "1269245000",
                      "mapy": "375219000"
                    }
                  ]
                }
                """);

        List<LocationSearchResponse> responses = client.search("여의도공원", 5);

        assertThat(responses).hasSize(1);
        LocationSearchResponse response = responses.getFirst();
        assertThat(response.id()).startsWith("naver:place:");
        assertThat(response.type()).isEqualTo("PLACE");
        assertThat(response.name()).isEqualTo("여의도공원");
        assertThat(response.category()).isEqualTo("여행,명소>공원");
        assertThat(response.address()).isEqualTo("서울특별시 영등포구 여의공원로 68");
        assertThat(response.latitude()).isEqualTo(37.5219);
        assertThat(response.longitude()).isEqualTo(126.9245);
        assertThat(response.provider()).isEqualTo("NAVER");
    }

    @Test
    void searchReturnsAddressResponseFromGeocoding() {
        NaverLocationClient client = createClient(mapsOnlyProperties());
        expectGeocode("서울 영등포구 여의공원로 68", """
                {
                  "addresses": [
                    {
                      "roadAddress": "서울특별시 영등포구 여의공원로 68",
                      "jibunAddress": "서울특별시 영등포구 여의도동 2",
                      "x": "126.9245",
                      "y": "37.5219"
                    }
                  ]
                }
                """);

        List<LocationSearchResponse> responses = client.search("서울 영등포구 여의공원로 68", 5);

        assertThat(responses).hasSize(1);
        LocationSearchResponse response = responses.getFirst();
        assertThat(response.id()).startsWith("naver:address:");
        assertThat(response.type()).isEqualTo("ADDRESS");
        assertThat(response.name()).isEqualTo("서울특별시 영등포구 여의공원로 68");
        assertThat(response.category()).isEqualTo("주소");
        assertThat(response.address()).isEqualTo("서울특별시 영등포구 여의공원로 68");
        assertThat(response.latitude()).isEqualTo(37.5219);
        assertThat(response.longitude()).isEqualTo(126.9245);
    }

    @Test
    void reverseReturnsAddressResponseFromReverseGeocoding() {
        NaverLocationClient client = createClient(mapsOnlyProperties());
        expectReverseGeocode(37.5219, 126.9245, """
                {
                  "status": {
                    "code": 0,
                    "name": "ok",
                    "message": "done"
                  },
                  "results": [
                    {
                      "name": "roadaddr",
                      "region": {
                        "area1": { "name": "서울특별시" },
                        "area2": { "name": "영등포구" },
                        "area3": { "name": "여의도동" },
                        "area4": { "name": "" }
                      },
                      "land": {
                        "name": "여의공원로",
                        "type": "1",
                        "number1": "68",
                        "number2": "",
                        "addition0": {
                          "type": "building",
                          "value": "여의도공원"
                        }
                      }
                    }
                  ]
                }
                """);

        LocationSearchResponse response = client.reverse(37.5219, 126.9245);

        assertThat(response.id()).startsWith("naver:address:");
        assertThat(response.type()).isEqualTo("ADDRESS");
        assertThat(response.name()).isEqualTo("여의도공원");
        assertThat(response.category()).isEqualTo("주소");
        assertThat(response.address()).isEqualTo("서울특별시 영등포구 여의공원로 68");
        assertThat(response.latitude()).isEqualTo(37.5219);
        assertThat(response.longitude()).isEqualTo(126.9245);
        assertThat(response.provider()).isEqualTo("NAVER");
    }

    @Test
    void reverseThrowsNotFoundWhenReverseGeocodingStatusIsNoResults() {
        NaverLocationClient client = createClient(mapsOnlyProperties());
        expectReverseGeocode(37.5219, 126.9245, """
                {
                  "status": {
                    "code": 3,
                    "name": "no results",
                    "message": "요청한 데이터의 결과가 없습니다."
                  },
                  "results": []
                }
                """);

        assertThatThrownBy(() -> client.reverse(37.5219, 126.9245))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("좌표에 해당하는 주소를 찾을 수 없습니다.");
    }

    @Test
    void reverseThrowsWhenReverseGeocodingStatusIsMissing() {
        NaverLocationClient client = createClient(mapsOnlyProperties());
        expectReverseGeocode(37.5219, 126.9245, """
                {
                  "results": [
                    {
                      "name": "roadaddr",
                      "region": {
                        "area1": { "name": "서울특별시" },
                        "area2": { "name": "영등포구" },
                        "area3": { "name": "" },
                        "area4": { "name": "" }
                      },
                      "land": {
                        "name": "여의공원로",
                        "type": "1",
                        "number1": "68",
                        "number2": ""
                      }
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> client.reverse(37.5219, 126.9245))
                .isInstanceOf(BaseException.class)
                .hasMessage("네이버 위치 역조회 응답이 올바르지 않습니다.");
    }

    @Test
    void reversePreservesMountainLotNumberForJibunAddress() {
        NaverLocationClient client = createClient(mapsOnlyProperties());
        expectReverseGeocode(37.7219, 127.6245, """
                {
                  "status": {
                    "code": 0,
                    "name": "ok",
                    "message": "done"
                  },
                  "results": [
                    {
                      "name": "addr",
                      "region": {
                        "area1": { "name": "강원특별자치도" },
                        "area2": { "name": "춘천시" },
                        "area3": { "name": "남산면" },
                        "area4": { "name": "" }
                      },
                      "land": {
                        "name": "",
                        "type": "2",
                        "number1": "5",
                        "number2": "6"
                      }
                    }
                  ]
                }
                """);

        LocationSearchResponse response = client.reverse(37.7219, 127.6245);

        assertThat(response.name()).isEqualTo("강원특별자치도 춘천시 남산면 산5-6");
        assertThat(response.address()).isEqualTo("강원특별자치도 춘천시 남산면 산5-6");
    }

    @Test
    void reverseThrowsNotFoundWhenReverseGeocodingReturnsNoUsableResult() {
        NaverLocationClient client = createClient(mapsOnlyProperties());
        expectReverseGeocode(37.5219, 126.9245, """
                {
                  "status": {
                    "code": 0,
                    "name": "ok",
                    "message": "done"
                  },
                  "results": []
                }
                """);

        assertThatThrownBy(() -> client.reverse(37.5219, 126.9245))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("좌표에 해당하는 주소를 찾을 수 없습니다.");
    }

    @Test
    void searchUsesGeocodingFallbackWhenLocalSearchCoordinateIsNotWgs84() {
        NaverLocationClient client = createClient(defaultProperties());
        expectGeocode("여의도공원", """
                {
                  "addresses": []
                }
                """);
        expectLocalSearch("여의도공원", """
                {
                  "items": [
                    {
                      "title": "<b>여의도공원</b>",
                      "category": "여행,명소&gt;공원",
                      "address": "서울특별시 영등포구 여의도동 2",
                      "roadAddress": "서울특별시 영등포구 여의공원로 68",
                      "mapx": "311277",
                      "mapy": "552097"
                    }
                  ]
                }
                """);
        expectGeocode("서울특별시 영등포구 여의공원로 68", """
                {
                  "addresses": [
                    {
                      "roadAddress": "서울특별시 영등포구 여의공원로 68",
                      "jibunAddress": "서울특별시 영등포구 여의도동 2",
                      "x": "126.9245",
                      "y": "37.5219"
                    }
                  ]
                }
                """);

        List<LocationSearchResponse> responses = client.search("여의도공원", 5);

        assertThat(responses).hasSize(1);
        LocationSearchResponse response = responses.getFirst();
        assertThat(response.type()).isEqualTo("PLACE");
        assertThat(response.latitude()).isEqualTo(37.5219);
        assertThat(response.longitude()).isEqualTo(126.9245);
    }

    @Test
    void searchDropsInvalidCoordinates() {
        NaverLocationClient client = createClient(searchOnlyProperties());
        expectLocalSearch("invalid", """
                {
                  "items": [
                    {
                      "title": "잘못된 위치",
                      "category": "",
                      "address": "주소",
                      "roadAddress": "",
                      "mapx": "311277",
                      "mapy": "552097"
                    }
                  ]
                }
                """);

        List<LocationSearchResponse> responses = client.search("invalid", 5);

        assertThat(responses).isEmpty();
    }

    @Test
    void searchThrowsWhenAllCredentialsAreMissing() {
        NaverLocationClient client = createClient(new NaverLocationProperties(
                "https://openapi.naver.com",
                "",
                "",
                "https://maps.apigw.ntruss.com",
                "",
                ""
        ));

        assertThatThrownBy(() -> client.search("여의도공원", 5))
                .isInstanceOf(BaseException.class)
                .hasMessage("네이버 위치 검색 설정이 누락되었습니다.");
    }

    @Test
    void reverseThrowsWhenMapsCredentialsAreMissing() {
        NaverLocationClient client = createClient(searchOnlyProperties());

        assertThatThrownBy(() -> client.reverse(37.5219, 126.9245))
                .isInstanceOf(BaseException.class)
                .hasMessage("네이버 지도 API 설정이 누락되었습니다.");
    }

    private NaverLocationClient createClient(NaverLocationProperties properties) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        return new NaverLocationClient(restClientBuilder, properties);
    }

    private NaverLocationProperties defaultProperties() {
        return new NaverLocationProperties(
                "https://openapi.naver.com",
                "search-client-id",
                "search-client-secret",
                "https://maps.apigw.ntruss.com",
                "maps-client-id",
                "maps-client-secret"
        );
    }

    private NaverLocationProperties searchOnlyProperties() {
        return new NaverLocationProperties(
                "https://openapi.naver.com",
                "search-client-id",
                "search-client-secret",
                "https://maps.apigw.ntruss.com",
                "",
                ""
        );
    }

    private NaverLocationProperties mapsOnlyProperties() {
        return new NaverLocationProperties(
                "https://openapi.naver.com",
                "",
                "",
                "https://maps.apigw.ntruss.com",
                "maps-client-id",
                "maps-client-secret"
        );
    }

    private void expectLocalSearch(String query, String body) {
        server.expect(requestTo(startsWith("https://openapi.naver.com/v1/search/local.json")))
                .andExpect(queryParam("query", encodedQuery(query)))
                .andExpect(queryParam("display", "5"))
                .andExpect(queryParam("start", "1"))
                .andExpect(queryParam("sort", "random"))
                .andExpect(header("X-Naver-Client-Id", "search-client-id"))
                .andExpect(header("X-Naver-Client-Secret", "search-client-secret"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectGeocode(String query, String body) {
        server.expect(requestTo(startsWith("https://maps.apigw.ntruss.com/map-geocode/v2/geocode")))
                .andExpect(queryParam("query", encodedQuery(query)))
                .andExpect(header("X-NCP-APIGW-API-KEY-ID", "maps-client-id"))
                .andExpect(header("X-NCP-APIGW-API-KEY", "maps-client-secret"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectReverseGeocode(double latitude, double longitude, String body) {
        server.expect(requestTo(startsWith("https://maps.apigw.ntruss.com/map-reversegeocode/v2/gc")))
                .andExpect(queryParam("coords", longitude + "%2C" + latitude))
                .andExpect(queryParam("orders", "roadaddr,addr"))
                .andExpect(queryParam("output", "json"))
                .andExpect(header("X-NCP-APIGW-API-KEY-ID", "maps-client-id"))
                .andExpect(header("X-NCP-APIGW-API-KEY", "maps-client-secret"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String encodedQuery(String query) {
        return switch (query) {
            case "여의도공원" -> ENCODED_YEOUIDO_PARK_QUERY;
            case "서울 영등포구 여의공원로 68" -> ENCODED_YEOUIDO_PARK_ADDRESS;
            case "서울특별시 영등포구 여의공원로 68" -> ENCODED_FULL_YEOUIDO_PARK_ADDRESS;
            default -> query;
        };
    }
}
