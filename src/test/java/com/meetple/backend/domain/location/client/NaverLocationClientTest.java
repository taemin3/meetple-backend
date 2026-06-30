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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverLocationClientTest {

    private static final String ENCODED_YEOUIDO_PARK_QUERY =
            "%EC%97%AC%EC%9D%98%EB%8F%84%EA%B3%B5%EC%9B%90";

    private MockRestServiceServer server;
    private NaverLocationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new NaverLocationClient(
                restClientBuilder,
                new NaverLocationProperties("https://openapi.naver.com", "client-id", "client-secret")
        );
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void searchMapsNaverLocalSearchResponse() {
        server.expect(requestTo(startsWith("https://openapi.naver.com/v1/search/local.json")))
                .andExpect(queryParam("query", ENCODED_YEOUIDO_PARK_QUERY))
                .andExpect(queryParam("display", "5"))
                .andExpect(queryParam("start", "1"))
                .andExpect(queryParam("sort", "random"))
                .andExpect(header("X-Naver-Client-Id", "client-id"))
                .andExpect(header("X-Naver-Client-Secret", "client-secret"))
                .andRespond(withSuccess("""
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
                        """, MediaType.APPLICATION_JSON));

        List<LocationSearchResponse> responses = client.search("여의도공원", 5);

        assertThat(responses).hasSize(1);
        LocationSearchResponse response = responses.getFirst();
        assertThat(response.id()).startsWith("naver:");
        assertThat(response.name()).isEqualTo("여의도공원");
        assertThat(response.category()).isEqualTo("여행,명소>공원");
        assertThat(response.address()).isEqualTo("서울특별시 영등포구 여의공원로 68");
        assertThat(response.latitude()).isEqualTo(37.5219);
        assertThat(response.longitude()).isEqualTo(126.9245);
        assertThat(response.provider()).isEqualTo("NAVER");
    }

    @Test
    void searchDropsInvalidCoordinates() {
        server.expect(requestTo(startsWith("https://openapi.naver.com/v1/search/local.json")))
                .andExpect(queryParam("query", "invalid"))
                .andRespond(withSuccess("""
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
                        """, MediaType.APPLICATION_JSON));

        List<LocationSearchResponse> responses = client.search("invalid", 5);

        assertThat(responses).isEmpty();
    }

    @Test
    void searchThrowsWhenCredentialsAreMissing() {
        NaverLocationClient missingCredentialClient = new NaverLocationClient(
                RestClient.builder(),
                new NaverLocationProperties("https://openapi.naver.com", "", "")
        );

        assertThatThrownBy(() -> missingCredentialClient.search("여의도공원", 5))
                .isInstanceOf(BaseException.class)
                .hasMessage("네이버 위치 검색 설정이 누락되었습니다.");
    }
}
