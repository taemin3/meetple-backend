package com.meetple.backend.domain.location.controller;

import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import com.meetple.backend.domain.location.service.LocationService;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "장소 검색", description = "네이버 지역 검색과 주소 변환 결과를 통합해 장소 후보를 조회합니다.")
    public ResponseEntity<ApiResponse<List<LocationSearchResponse>>> searchLocations(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(5) int display
    ) {
        if (!StringUtils.hasText(query)) {
            throw new BadRequestException("검색어를 입력해주세요.");
        }
        return ApiResponse.success(SuccessStatus.OK, locationService.search(query, display));
    }

    @GetMapping("/reverse")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "좌표 주소 조회", description = "위도와 경도를 기준으로 네이버 Reverse Geocoding 주소 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<LocationSearchResponse>> reverseLocation(
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
    ) {
        if (latitude == null || longitude == null) {
            throw new BadRequestException("위도와 경도를 입력해주세요.");
        }
        return ApiResponse.success(SuccessStatus.OK, locationService.reverse(latitude, longitude));
    }
}
