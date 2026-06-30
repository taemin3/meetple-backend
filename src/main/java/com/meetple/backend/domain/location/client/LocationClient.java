package com.meetple.backend.domain.location.client;

import com.meetple.backend.domain.location.dto.response.LocationSearchResponse;
import java.util.List;

public interface LocationClient {

    List<LocationSearchResponse> search(String query, int display);
}
