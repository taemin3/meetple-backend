package com.meetple.backend.domain.meeting.service;

import com.meetple.backend.domain.image.service.ImageDeletionService;
import com.meetple.backend.domain.meeting.repository.MeetingBookmarkRepository;
import com.meetple.backend.domain.meeting.repository.MeetingImageRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeetingDeletionPurgeService {

    private static final int RETENTION_DAYS = 30;
    private static final int BATCH_SIZE = 20;

    private final MeetingRepository meetingRepository;
    private final MeetingImageRepository meetingImageRepository;
    private final MeetingBookmarkRepository bookmarkRepository;
    private final ImageDeletionService imageDeletionService;

    @Scheduled(fixedDelayString = "${deletion.meeting-purge-interval-ms:3600000}")
    @Transactional
    public int purgeExpiredMeetings() {
        List<Long> meetingIds = meetingRepository.findPurgeCandidateIds(
                LocalDateTime.now().minusDays(RETENTION_DAYS),
                PageRequest.of(0, BATCH_SIZE)
        );
        if (meetingIds.isEmpty()) {
            return 0;
        }

        imageDeletionService.schedule(
                meetingImageRepository.findObjectKeysIncludingDeletedMeetings(meetingIds)
        );
        bookmarkRepository.deleteByMeetingIdInIncludingDeletedMeetings(meetingIds);
        meetingImageRepository.deleteByMeetingIdInIncludingDeletedMeetings(meetingIds);
        return meetingRepository.deletePermanentlyByIdIn(meetingIds);
    }
}
