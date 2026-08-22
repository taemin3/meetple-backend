package com.meetple.backend.domain.legal.entity;

import com.meetple.backend.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "member_legal_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_legal_records_member_document",
                        columnNames = {"member_id", "legal_document_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberLegalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_document_id", nullable = false)
    private LegalDocument legalDocument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LegalDocumentAction action;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    private MemberLegalRecord(Member member, LegalDocument legalDocument) {
        this.member = member;
        this.legalDocument = legalDocument;
        this.action = legalDocument.getType().getAction();
    }

    public static MemberLegalRecord signup(Member member, LegalDocument legalDocument) {
        return new MemberLegalRecord(member, legalDocument);
    }

    @PrePersist
    void recordTime() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
