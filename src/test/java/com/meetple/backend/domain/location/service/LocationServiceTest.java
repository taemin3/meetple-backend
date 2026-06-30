package com.meetple.backend.domain.location.service;

import static org.mockito.BDDMockito.then;

import com.meetple.backend.domain.location.client.LocationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationClient locationClient;

    @InjectMocks
    private LocationService locationService;

    @Test
    void searchTrimsQuery() {
        locationService.search(" 여의도공원 ", 5);

        then(locationClient).should().search("여의도공원", 5);
    }
}
