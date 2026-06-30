package com.meetple.backend.domain.location.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import com.meetple.backend.domain.location.service.LocationService;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new LocationController(locationService)).build();
    }

    @Test
    void searchLocationsReturnsApiResponse() throws Exception {
        given(locationService.search("여의도공원", 5))
                .willReturn(List.of(new LocationSearchResponse(
                        "naver:1",
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
                .andExpect(jsonPath("$.data[0].name").value("여의도공원"))
                .andExpect(jsonPath("$.data[0].address").value("서울특별시 영등포구 여의공원로 68"))
                .andExpect(jsonPath("$.data[0].latitude").value(37.5219))
                .andExpect(jsonPath("$.data[0].longitude").value(126.9245))
                .andExpect(jsonPath("$.data[0].provider").value("NAVER"));
    }
}
