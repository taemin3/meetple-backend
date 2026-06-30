package com.meetple.backend.domain.location.controller;

import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import com.meetple.backend.domain.location.service.LocationService;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Location", description = "위치 검색 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/search")
    @Operation(summary = "장소 검색", description = "네이버 지역 검색 API를 통해 장소 후보를 조회합니다.")
    public ResponseEntity<ApiResponse<List<LocationSearchResponse>>> searchLocations(
            @RequestParam @NotBlank(message = "검색어를 입력해주세요.") String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(5) int display
    ) {
        return ApiResponse.success(SuccessStatus.OK, locationService.search(query, display));
    }
}
