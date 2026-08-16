package com.meetple.backend.domain.meeting.entity;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@SQLRestriction("deleted_at is null")
@Table(
        name = "meetings",
        indexes = {
                @Index(name = "idx_meetings_host_id", columnList = "host_id"),
                @Index(name = "idx_meetings_category_id", columnList = "category_id"),
                @Index(name = "idx_meetings_status_meeting_date", columnList = "status, meeting_date")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "location_name", nullable = false, length = 255)
    private String locationName;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(name = "max_people", nullable = false)
    private Integer maxPeople;

    @Column(name = "current_people", nullable = false)
    private Integer currentPeople;

    @Column(name = "meeting_date", nullable = false)
    private LocalDateTime meetingDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MeetingStatus status;

    @Column(name = "thumbnail_image_object_key", length = 255)
    private String thumbnailImageObjectKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    private Meeting(
            Member host,
            Category category,
            String title,
            String content,
            String locationName,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer maxPeople,
            LocalDateTime meetingDate,
            LocalDateTime endDate,
            String thumbnailImageObjectKey
    ) {
        this.host = host;
        this.category = category;
        this.title = title;
        this.content = content;
        this.locationName = locationName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxPeople = maxPeople;
        this.currentPeople = 1;
        this.meetingDate = meetingDate;
        this.endDate = endDate;
        this.thumbnailImageObjectKey = thumbnailImageObjectKey;
        this.status = MeetingStatus.RECRUITING;
    }

    public static Meeting create(
            Member host,
            Category category,
            String title,
            String content,
            String locationName,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer maxPeople,
            LocalDateTime meetingDate,
            String thumbnailImageObjectKey
    ) {
        return create(
                host,
                category,
                title,
                content,
                locationName,
                address,
                latitude,
                longitude,
                maxPeople,
                meetingDate,
                null,
                thumbnailImageObjectKey
        );
    }

    public static Meeting create(
            Member host,
            Category category,
            String title,
            String content,
            String locationName,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer maxPeople,
            LocalDateTime meetingDate,
            LocalDateTime endDate,
            String thumbnailImageObjectKey
    ) {
        return new Meeting(
                host,
                category,
                title,
                content,
                locationName,
                address,
                latitude,
                longitude,
                maxPeople,
                meetingDate,
                endDate,
                thumbnailImageObjectKey
        );
    }

    public void complete() {
        this.status = MeetingStatus.COMPLETED;
    }

    public void cancel(String reason) {
        this.status = MeetingStatus.CANCELED;
        this.cancelReason = reason;
    }

    public void update(
            Category category,
            String title,
            String content,
            String locationName,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer maxPeople,
            LocalDateTime meetingDate,
            LocalDateTime endDate
    ) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.locationName = locationName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxPeople = maxPeople;
        this.meetingDate = meetingDate;
        this.endDate = endDate;
        updateRecruitmentStatusByCapacity();
    }

    public void changeThumbnailImageObjectKey(String thumbnailImageObjectKey) {
        this.thumbnailImageObjectKey = thumbnailImageObjectKey;
    }

    public void softDelete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isHostedBy(Long memberId) {
        return this.host.getId().equals(memberId);
    }

    public boolean isClosed() {
        return this.status == MeetingStatus.COMPLETED || this.status == MeetingStatus.CANCELED;
    }

    public void increaseCurrentPeople() {
        if (this.currentPeople < this.maxPeople) {
            this.currentPeople++;
        }
        updateRecruitmentStatusByCapacity();
    }

    public void decreaseCurrentPeople() {
        if (this.currentPeople > 1) {
            this.currentPeople--;
        }
        updateRecruitmentStatusByCapacity();
    }

    private void updateRecruitmentStatusByCapacity() {
        if (this.status == MeetingStatus.COMPLETED || this.status == MeetingStatus.CANCELED) {
            return;
        }

        if (this.currentPeople >= this.maxPeople) {
            this.status = MeetingStatus.FULL;
            return;
        }

        if (this.status == MeetingStatus.FULL) {
            this.status = MeetingStatus.RECRUITING;
        }
    }
}
