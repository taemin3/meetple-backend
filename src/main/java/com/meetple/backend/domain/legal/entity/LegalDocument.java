package com.meetple.backend.domain.legal.entity;

import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "legal_documents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_legal_documents_type_version",
                        columnNames = {"type", "version"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private LegalDocumentType type;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    private LegalDocument(
            LegalDocumentType type,
            String version,
            String title,
            String content,
            LocalDateTime effectiveAt
    ) {
        this.type = type;
        this.version = version;
        this.title = title;
        this.content = content;
        this.effectiveAt = effectiveAt;
    }

    public static LegalDocument create(
            LegalDocumentType type,
            String version,
            String title,
            String content,
            LocalDateTime effectiveAt
    ) {
        return new LegalDocument(type, version, title, content, effectiveAt);
    }
}
