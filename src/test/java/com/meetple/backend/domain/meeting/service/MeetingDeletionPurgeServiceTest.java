package com.meetple.backend.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.meetple.backend.domain.image.service.ImageDeletionService;
import com.meetple.backend.domain.meeting.repository.MeetingBookmarkRepository;
import com.meetple.backend.domain.meeting.repository.MeetingImageRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingDeletionPurgeServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingImageRepository meetingImageRepository;

    @Mock
    private MeetingBookmarkRepository bookmarkRepository;

    @Mock
    private ImageDeletionService imageDeletionService;

    @InjectMocks
    private MeetingDeletionPurgeService purgeService;

    @Test
    void purgeExpiredMeetingsSchedulesImagesBeforeDeletingRows() {
        List<Long> meetingIds = List.of(10L, 11L);
        List<String> objectKeys = List.of("images/meeting/1/first.png", "images/meeting/1/second.png");
        given(meetingRepository.findPurgeCandidateIds(any(), any())).willReturn(meetingIds);
        given(meetingImageRepository.findObjectKeysIncludingDeletedMeetings(meetingIds)).willReturn(objectKeys);
        given(meetingRepository.deletePermanentlyByIdIn(meetingIds)).willReturn(2);

        int purged = purgeService.purgeExpiredMeetings();

        assertThat(purged).isEqualTo(2);
        InOrder order = inOrder(
                imageDeletionService,
                bookmarkRepository,
                meetingImageRepository,
                meetingRepository
        );
        order.verify(imageDeletionService).schedule(objectKeys);
        order.verify(bookmarkRepository).deleteByMeetingIdInIncludingDeletedMeetings(meetingIds);
        order.verify(meetingImageRepository).deleteByMeetingIdInIncludingDeletedMeetings(meetingIds);
        order.verify(meetingRepository).deletePermanentlyByIdIn(meetingIds);
    }
}
