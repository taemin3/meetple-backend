package com.meetple.backend.domain.moderation.policy;

import static com.meetple.backend.domain.moderation.policy.AdminModerationPolicyContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.moderation.policy.AdminModerationPolicyRepository.ClauseRow;
import com.meetple.backend.domain.moderation.policy.AdminModerationPolicyRepository.PolicyRow;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AdminModerationPolicyServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-02T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 9, 0);

    @Mock AdminModerationPolicyRepository repository;

    private AdminModerationPolicyService service;

    @BeforeEach
    void setUp() {
        service = new AdminModerationPolicyService(repository, CLOCK);
    }

    @Test
    void createPolicyStoresInactiveVersionWithContentHashesAndAudit() {
        CreatePolicyRequest request = new CreatePolicyRequest(
                "COMMUNITY-SPAM",
                "스팸 금지",
                ModerationPolicyType.SPAM,
                ModerationPolicyTargetType.ALL,
                LocalDate.of(2026, 10, 3),
                null,
                List.of(
                        new ClauseRequest("SPAM-1", " 반복 광고를 금지합니다. "),
                        new ClauseRequest("SPAM-2", "자동 홍보를 금지합니다.")
                )
        );
        given(repository.insertPolicy(
                eq("COMMUNITY-SPAM"),
                eq("스팸 금지"),
                eq(ModerationPolicyType.SPAM),
                eq(ModerationPolicyTargetType.ALL),
                eq(LocalDate.of(2026, 10, 3)),
                eq(null),
                eq(1),
                eq(NOW)
        )).willReturn(10L);
        given(repository.findById(10L)).willReturn(Optional.of(policy(10L, 1, false)));
        given(repository.findClauses(10L, AdminModerationPolicyService.EMBEDDING_MODEL))
                .willReturn(List.of());
        given(repository.findAudits(10L)).willReturn(List.of());

        PolicyDetail result = service.createPolicy(7L, request);

        ArgumentCaptor<List<ClauseRow>> clausesCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertClauses(eq(10L), clausesCaptor.capture(), eq(NOW));
        assertThat(clausesCaptor.getValue()).satisfiesExactly(
                clause -> {
                    assertThat(clause.clauseCode()).isEqualTo("SPAM-1");
                    assertThat(clause.chunkOrder()).isZero();
                    assertThat(clause.content()).isEqualTo("반복 광고를 금지합니다.");
                    assertThat(clause.contentHash()).hasSize(64);
                },
                clause -> assertThat(clause.chunkOrder()).isEqualTo(1)
        );
        verify(repository).insertAudit(10L, 7L, PolicyAuditAction.CREATED, NOW);
        assertThat(result.active()).isFalse();
    }

    @Test
    void duplicateClauseCodesAreRejectedBeforeInsert() {
        CreatePolicyRequest request = new CreatePolicyRequest(
                "COMMUNITY-SPAM",
                "스팸 금지",
                ModerationPolicyType.SPAM,
                ModerationPolicyTargetType.ALL,
                LocalDate.of(2026, 10, 3),
                null,
                List.of(
                        new ClauseRequest("SPAM-1", "첫 번째"),
                        new ClauseRequest("SPAM-1", "두 번째")
                )
        );

        assertThatThrownBy(() -> service.createPolicy(7L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("같은 정책 안에서 조항 코드는 중복될 수 없습니다.");

        verify(repository, never()).insertPolicy(any(), any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void newVersionCanOnlyBeCreatedFromLatestVersion() {
        PolicyRow source = policy(10L, 1, true);
        given(repository.findById(10L)).willReturn(Optional.of(source));
        given(repository.findByIdForUpdate(10L)).willReturn(Optional.of(source));
        given(repository.lockVersionsAndFindLatest("COMMUNITY-SPAM")).willReturn(2);

        assertThatThrownBy(() -> service.createVersion(7L, 10L, versionRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("최신 버전의 운영 정책에서만 새 버전을 만들 수 있습니다.");

        verify(repository, never()).insertClauses(anyLong(), any(), any());
    }

    @Test
    void newVersionIsCreatedInactiveWithIncrementedVersionAndAudit() {
        PolicyRow source = policy(10L, 1, true);
        given(repository.findById(10L)).willReturn(Optional.of(source));
        given(repository.findByIdForUpdate(10L)).willReturn(Optional.of(source));
        given(repository.lockVersionsAndFindLatest("COMMUNITY-SPAM")).willReturn(1);
        given(repository.insertPolicy(
                eq("COMMUNITY-SPAM"),
                eq("스팸 금지 개정"),
                eq(ModerationPolicyType.SPAM),
                eq(ModerationPolicyTargetType.ALL),
                eq(LocalDate.of(2026, 10, 3)),
                eq(null),
                eq(2),
                eq(NOW)
        )).willReturn(11L);
        given(repository.findById(11L)).willReturn(Optional.of(policy(11L, 2, false)));
        given(repository.findClauses(11L, AdminModerationPolicyService.EMBEDDING_MODEL))
                .willReturn(List.of());
        given(repository.findAudits(11L)).willReturn(List.of());

        PolicyDetail result = service.createVersion(7L, 10L, versionRequest());

        verify(repository).insertClauses(eq(11L), any(), eq(NOW));
        verify(repository).insertAudit(11L, 7L, PolicyAuditAction.VERSION_CREATED, NOW);
        assertThat(result.version()).isEqualTo(2);
        assertThat(result.active()).isFalse();
    }

    @Test
    void activationIsRejectedUntilEveryClauseHasCurrentModelEmbedding() {
        PolicyRow policy = policy(10L, 2, false);
        given(repository.findById(10L)).willReturn(Optional.of(policy));
        given(repository.findByIdForUpdate(10L)).willReturn(Optional.of(policy));
        given(repository.countMissingEmbeddings(
                10L,
                AdminModerationPolicyService.EMBEDDING_MODEL
        )).willReturn(1);

        assertThatThrownBy(() -> service.updateActivation(
                7L,
                10L,
                new ActivationRequest(true)
        )).isInstanceOf(ConflictException.class)
                .hasMessage("모든 정책 조항의 임베딩을 동기화한 뒤 활성화할 수 있습니다.");

        verify(repository, never()).updateActivation(anyLong(), anyBoolean(), any());
    }

    @Test
    void activationDeactivatesPreviousVersionAndRecordsAudit() {
        PolicyRow inactive = policy(10L, 2, false);
        PolicyRow active = policy(10L, 2, true);
        given(repository.findById(10L))
                .willReturn(Optional.of(inactive), Optional.of(active));
        given(repository.findByIdForUpdate(10L)).willReturn(Optional.of(inactive));
        given(repository.countMissingEmbeddings(
                10L,
                AdminModerationPolicyService.EMBEDDING_MODEL
        )).willReturn(0);
        given(repository.deactivateOtherVersions(10L, "COMMUNITY-SPAM", NOW))
                .willReturn(List.of(9L));
        given(repository.findClauses(10L, AdminModerationPolicyService.EMBEDDING_MODEL))
                .willReturn(List.of());
        given(repository.findAudits(10L)).willReturn(List.of());

        PolicyDetail result = service.updateActivation(7L, 10L, new ActivationRequest(true));

        verify(repository).deactivateOtherVersions(10L, "COMMUNITY-SPAM", NOW);
        verify(repository).insertAudit(9L, 7L, PolicyAuditAction.DEACTIVATED, NOW);
        verify(repository).updateActivation(10L, true, NOW);
        verify(repository).insertAudit(10L, 7L, PolicyAuditAction.ACTIVATED, NOW);
        assertThat(result.active()).isTrue();
    }

    @Test
    void contentHashIsStableForUtf8PolicyText() {
        assertThat(AdminModerationPolicyService.sha256(
                "동일하거나 유사한 광고성 내용을 반복해서 게시하거나 전송해서는 안 됩니다."
        )).isEqualTo("6af25c64ef45abbfce522a3e0b40172949fa60dd976936b7bb486dc4576ed655");
    }

    private static CreateVersionRequest versionRequest() {
        return new CreateVersionRequest(
                "스팸 금지 개정",
                ModerationPolicyType.SPAM,
                ModerationPolicyTargetType.ALL,
                LocalDate.of(2026, 10, 3),
                null,
                List.of(new ClauseRequest("SPAM-1", "개정 조항"))
        );
    }

    private static PolicyRow policy(long id, int version, boolean active) {
        return new PolicyRow(
                id,
                "COMMUNITY-SPAM",
                "스팸 금지",
                ModerationPolicyType.SPAM,
                ModerationPolicyTargetType.ALL,
                LocalDate.of(2026, 10, 1),
                null,
                active,
                version,
                NOW,
                NOW
        );
    }
}
