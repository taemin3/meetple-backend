package com.meetple.backend.domain.location.dto.response;

public record LocationSearchResponse(
        String id,
        String type,
        String name,
        String category,
        String address,
        double latitude,
        double longitude,
        String provider
) {
}
