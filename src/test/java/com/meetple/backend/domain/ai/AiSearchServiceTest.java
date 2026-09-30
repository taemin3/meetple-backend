package com.meetple.backend.domain.ai;

import static com.meetple.backend.domain.ai.AiSearchContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.meetple.backend.global.exception.BaseException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AiSearchServiceTest {
    private final AiMeetingSearchRepository repository = mock(AiMeetingSearchRepository.class);
    private final AiSearchService service = new AiSearchService(AiSearchCapabilityTest.PROPERTIES,
            new AiSearchCapability(AiSearchCapabilityTest.PROPERTIES), repository, RestClient.builder());
    private final Request request = new Request("초보 러닝", 37.5, 127.0, 3000);
    private final Filters filters = new Filters("러닝", "운동", LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(3), null, null, 37.5, 127, 3000);
    private final Candidate candidate = new Candidate(10, "러닝 모임", "처음 달리는 분 환영", "운동", "공원",
            LocalDateTime.now().plusDays(1), null, 6, 2, 300);

    @Test void validatesEvidenceAgainstFreshPermissionFilteredCandidates() {
        when(repository.search(eq(42L), any())).thenReturn(new Candidates(List.of(candidate), false));
        assertThatCode(() -> service.validateResponse(42, request, response(10, "처음 달리는 분 환영", filters)))
                .doesNotThrowAnyException();
        verify(repository).search(eq(42L), any());
    }

    @Test void rejectsUnknownIdsAndFabricatedQuotes() {
        when(repository.search(eq(42L), any())).thenReturn(new Candidates(List.of(candidate), false));
        assertThatThrownBy(() -> service.validateResponse(42, request, response(99, "처음 달리는 분 환영", filters)))
                .isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.validateResponse(42, request, response(10, "참가비 무료", filters)))
                .isInstanceOf(BaseException.class);
    }

    @Test void rejectsResultsHiddenByBlockOrRecruitmentChange() {
        when(repository.search(eq(42L), any())).thenReturn(new Candidates(List.of(), false));
        assertThatThrownBy(() -> service.validateResponse(42, request, response(10, "처음 달리는 분 환영", filters)))
                .isInstanceOf(BaseException.class);
    }

    @Test void modelCannotChangeSearchCenterOrExpandRadius() {
        var expanded = new Filters("러닝", "운동", filters.startsAt(), filters.endsBefore(),
                null, null, 37.5, 127, 5000);
        var moved = new Filters("러닝", "운동", filters.startsAt(), filters.endsBefore(),
                null, null, 35, 127, 3000);
        assertThatThrownBy(() -> service.validateResponse(42, request, response(10, "러닝", expanded)))
                .isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.validateResponse(42, request, response(10, "러닝", moved)))
                .isInstanceOf(BaseException.class);
        verifyNoInteractions(repository);
    }

    @Test void terminalStatusMustNotContainRecommendations() {
        var result = new Response(Status.INPUT_REQUIRED, "위치 필요", null, List.of(), "keyword");
        assertThatCode(() -> service.validateResponse(42, request, result)).doesNotThrowAnyException();
        verifyNoInteractions(repository);
    }

    private Response response(long id, String quote, Filters f) {
        return new Response(Status.COMPLETED, "검색 결과", f, List.of(new Recommendation(id, quote)), "keyword");
    }
}
