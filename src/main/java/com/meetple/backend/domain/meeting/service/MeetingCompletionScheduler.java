package com.meetple.backend.domain.meeting.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MeetingCompletionScheduler {

    private final MeetingService meetingService;

    @Scheduled(fixedDelay = 60_000)
    public void completeEndedMeetings() {
        meetingService.completeEndedMeetings(LocalDateTime.now());
    }
}
