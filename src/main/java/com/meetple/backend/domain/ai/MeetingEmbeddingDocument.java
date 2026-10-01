package com.meetple.backend.domain.ai;

import com.meetple.backend.domain.meeting.entity.Meeting;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record MeetingEmbeddingDocument(
        long meetingId,
        String title,
        String category,
        String locationName,
        String address,
        String description,
        String document,
        String contentHash
) {
    public static MeetingEmbeddingDocument from(Meeting meeting) {
        return from(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getCategory().getName(),
                meeting.getLocationName(),
                meeting.getAddress(),
                meeting.getContent()
        );
    }

    public static MeetingEmbeddingDocument from(
            long meetingId,
            String title,
            String category,
            String locationName,
            String address,
            String description
    ) {
        String document = "제목: " + title
                + "\n카테고리: " + category
                + "\n장소: " + locationName
                + "\n주소: " + address
                + "\n소개: " + description;
        return new MeetingEmbeddingDocument(
                meetingId,
                title,
                category,
                locationName,
                address,
                description,
                document,
                sha256(document)
        );
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
