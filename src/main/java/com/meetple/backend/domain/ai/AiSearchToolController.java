package com.meetple.backend.domain.ai;

import static com.meetple.backend.domain.ai.AiSearchContracts.*;

import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 로그인 JWT 대신 검색 전용 서명을 확인하는 내부 읽기 API. 공개 ALB에서 차단할 경로. */
@RestController
@RequestMapping("/internal/ai/search")
@RequiredArgsConstructor
public class AiSearchToolController {
    private final AiSearchCapability capability;
    private final AiMeetingSearchRepository repository;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> categories(
            @RequestHeader(value = "X-AI-Service-Token", required = false) String serviceToken,
            @RequestHeader(value = "X-Meetple-Capability", required = false) String token
    ) {
        capability.verify(serviceToken, token);
        return ApiResponse.success(SuccessStatus.OK, repository.categories());
    }

    @PostMapping("/meetings")
    public ResponseEntity<ApiResponse<Candidates>> search(
            @RequestHeader(value = "X-AI-Service-Token", required = false) String serviceToken,
            @RequestHeader(value = "X-Meetple-Capability", required = false) String token,
            @RequestBody Filters filters
    ) {
        long memberId = capability.verify(serviceToken, token);
        validateFilters(filters);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        if (filters.endsBefore().isBefore(now) || filters.endsBefore().isEqual(now)) {
            return ApiResponse.success(SuccessStatus.OK, new Candidates(List.of(), false));
        }
        Filters bounded = new Filters(filters.keyword(), filters.category(),
                filters.startsAt().isBefore(now) ? now : filters.startsAt(), filters.endsBefore(),
                filters.startsAtTime(), filters.endsBeforeTime(),
                filters.latitude(), filters.longitude(), filters.radiusMeters());
        return ApiResponse.success(SuccessStatus.OK, repository.search(memberId, bounded));
    }

    static void validateFilters(Filters f) {
        if (f == null || !Double.isFinite(f.latitude()) || !Double.isFinite(f.longitude())
                || Math.abs(f.latitude()) > 90 || Math.abs(f.longitude()) > 180
                || f.radiusMeters() < 100 || f.radiusMeters() > 50000
                || f.startsAt() == null || f.endsBefore() == null || !f.startsAt().isBefore(f.endsBefore())
                || (f.startsAtTime() != null && f.endsBeforeTime() != null
                    && !f.startsAtTime().isBefore(f.endsBeforeTime()))
                || f.endsBefore().isAfter(f.startsAt().plusDays(366))
                || (f.keyword() != null && f.keyword().length() > 100)
                || (f.category() != null && f.category().length() > 30)) {
            throw new BadRequestException("AI 검색 조건이 올바르지 않습니다.");
        }
    }
}
