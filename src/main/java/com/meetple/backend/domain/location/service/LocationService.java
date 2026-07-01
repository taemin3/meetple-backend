package com.meetple.backend.domain.location.service;

import com.meetple.backend.domain.location.client.LocationClient;
import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationClient locationClient;

    public List<LocationSearchResponse> search(String query, int display) {
        return locationClient.search(query.trim(), display);
    }
}
