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

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private MeetingImage(Meeting meeting, String objectKey, Integer sortOrder) {
        this.meeting = meeting;
        this.objectKey = objectKey;
        this.sortOrder = sortOrder;
    }

    public static MeetingImage create(Meeting meeting, String objectKey, Integer sortOrder) {
        return new MeetingImage(meeting, objectKey, sortOrder);
    }
}
