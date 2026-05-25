package com.meetple.backend.domain.meeting.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record MeetingPageResponse(
        List<MeetingResponse> meetings,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static MeetingPageResponse from(Page<MeetingResponse> page) {
        return new MeetingPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
