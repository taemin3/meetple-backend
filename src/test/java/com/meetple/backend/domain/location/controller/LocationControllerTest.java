package com.meetple.backend.domain.location.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import com.meetple.backend.domain.location.service.LocationService;
import com.meetple.backend.global.exception.GlobalExceptionHandler;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.response.SuccessStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    @Mock
    private LocationService locationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LocationController(locationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchLocationsReturnsApiResponse() throws Exception {
        given(locationService.search("여의도공원", 5))
                .willReturn(List.of(new LocationSearchResponse(
                        "naver:1",
                        "PLACE",
                        "여의도공원",
                        "여행,명소>공원",
                        "서울특별시 영등포구 여의공원로 68",
                        37.5219,
                        126.9245,
                        "NAVER"
                )));

        mockMvc.perform(get("/api/v1/locations/search")
                        .param("query", "여의도공원"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data[0].id").value("naver:1"))
                .andExpect(jsonPath("$.data[0].type").value("PLACE"))
                .andExpect(jsonPath("$.data[0].name").value("여의도공원"))
                .andExpect(jsonPath("$.data[0].address").value("서울특별시 영등포구 여의공원로 68"))
                .andExpect(jsonPath("$.data[0].latitude").value(37.5219))
                .andExpect(jsonPath("$.data[0].longitude").value(126.9245))
                .andExpect(jsonPath("$.data[0].provider").value("NAVER"));
    }

    @Test
    void searchLocationsWithoutQueryReturnsApiResponse() throws Exception {
        mockMvc.perform(get("/api/v1/locations/search")
                        .param("display", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("검색어를 입력해주세요."));
    }

    @Test
    void reverseLocationReturnsApiResponse() throws Exception {
        given(locationService.reverse(37.5219, 126.9245))
                .willReturn(new LocationSearchResponse(
                        "naver:address:1",
                        "ADDRESS",
                        "여의도공원",
                        "주소",
                        "서울특별시 영등포구 여의공원로 68",
                        37.5219,
                        126.9245,
                        "NAVER"
                ));

        mockMvc.perform(get("/api/v1/locations/reverse")
                        .param("latitude", "37.5219")
                        .param("longitude", "126.9245"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data.id").value("naver:address:1"))
                .andExpect(jsonPath("$.data.type").value("ADDRESS"))
                .andExpect(jsonPath("$.data.name").value("여의도공원"))
                .andExpect(jsonPath("$.data.category").value("주소"))
                .andExpect(jsonPath("$.data.address").value("서울특별시 영등포구 여의공원로 68"))
                .andExpect(jsonPath("$.data.latitude").value(37.5219))
                .andExpect(jsonPath("$.data.longitude").value(126.9245))
                .andExpect(jsonPath("$.data.provider").value("NAVER"));
    }
}
