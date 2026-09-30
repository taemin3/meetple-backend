package com.meetple.backend.domain.ai;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class AiSearchContracts {
    private AiSearchContracts() {}

    public record Request(
            @NotBlank @Size(max = 1000) String query,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @Min(100) @Max(50000) Integer radiusMeters
    ) {
        @AssertTrue(message = "위도와 경도를 함께 전달해주세요.")
        public boolean isLocationPairValid() { return (latitude == null) == (longitude == null); }
    }

    public record InternalRequest(
            String query, Double latitude, Double longitude, int radiusMeters, LocalDateTime referenceTime
    ) {}

    public record Filters(
            String keyword, String category, LocalDateTime startsAt, LocalDateTime endsBefore,
            LocalTime startsAtTime, LocalTime endsBeforeTime,
            double latitude, double longitude, int radiusMeters
    ) {}

    public record Candidate(
            long id, String title, String description, String categoryName, String locationName,
            LocalDateTime scheduledAt, LocalDateTime endsAt, int capacity, int currentPeople,
            double distanceMeters
    ) {}

    public record Candidates(List<Candidate> items, boolean hasMore) {}
    public record Recommendation(long meetingId, String evidenceQuote) {}
    public enum Status { COMPLETED, NO_RESULTS, INPUT_REQUIRED, UNSUPPORTED }
    public record Response(Status status, String message, Filters filters,
                           List<Recommendation> recommendations, String retrievalMode) {}
}
