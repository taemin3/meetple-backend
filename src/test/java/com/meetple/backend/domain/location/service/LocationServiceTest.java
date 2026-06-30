package com.meetple.backend.domain.location.service;

import static org.mockito.BDDMockito.then;

import com.meetple.backend.domain.location.client.LocationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LocationServiceTest {

    @Mock
    private LocationClient locationClient;

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        locationService = new LocationService(locationClient);
    }

    @Test
    void searchTrimsQuery() {
        locationService.search(" 여의도공원 ", 5);

        then(locationClient).should().search("여의도공원", 5);
    }
}
