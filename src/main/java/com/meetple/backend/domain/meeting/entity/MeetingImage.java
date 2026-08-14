package com.meetple.backend.domain.meeting.entity;

import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "meeting_images",
        indexes = {
                @Index(name = "idx_meeting_images_meeting_id", columnList = "meeting_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_meeting_images_meeting_sort_order",
                        columnNames = {"meeting_id", "sort_order"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "object_key", length = 255)
    private String objectKey;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private MeetingImage(Meeting meeting, String imageUrl, Integer sortOrder) {
        this.meeting = meeting;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    private MeetingImage(Meeting meeting, String objectKey, String imageUrl, Integer sortOrder) {
        this.meeting = meeting;
        this.objectKey = objectKey;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    public static MeetingImage create(Meeting meeting, String imageUrl, Integer sortOrder) {
        return new MeetingImage(meeting, imageUrl, sortOrder);
    }

    public static MeetingImage createWithObjectKey(
            Meeting meeting,
            String objectKey,
            String imageUrl,
            Integer sortOrder
    ) {
        return new MeetingImage(meeting, objectKey, imageUrl, sortOrder);
    }
}
