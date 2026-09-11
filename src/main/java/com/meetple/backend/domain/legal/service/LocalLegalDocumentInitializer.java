package com.meetple.backend.domain.legal.service;

import com.meetple.backend.domain.legal.entity.LegalDocument;
import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import com.meetple.backend.domain.legal.repository.LegalDocumentRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalLegalDocumentInitializer implements ApplicationRunner {

    private static final String INITIAL_VERSION = "2026-08-22";
    private static final LocalDateTime INITIAL_EFFECTIVE_AT = LocalDateTime.of(2026, 8, 22, 0, 0);
    private static final String CURRENT_PRIVACY_POLICY_VERSION = "2026-09-12.1";
    private static final LocalDateTime CURRENT_PRIVACY_POLICY_EFFECTIVE_AT =
            LocalDateTime.of(2026, 9, 12, 0, 1);

    private final LegalDocumentRepository legalDocumentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initialDocuments().stream()
                .filter(document -> !legalDocumentRepository.existsByTypeAndVersion(
                        document.getType(),
                        document.getVersion()
                ))
                .forEach(legalDocumentRepository::save);
    }

    private List<LegalDocument> initialDocuments() {
        return List.of(
                LegalDocument.create(
                        LegalDocumentType.SERVICE_TERMS,
                        INITIAL_VERSION,
                        "서비스 이용약관",
                        "밋플 서비스는 사용자가 모임을 만들고 참여할 수 있도록 지원합니다. "
                                + "사용자는 타인의 권리를 침해하거나 서비스 운영을 방해해서는 안 되며, "
                                + "운영정책을 위반한 콘텐츠와 계정은 제한될 수 있습니다. "
                                + "회사는 안정적인 서비스 제공을 위해 기능을 변경할 수 있고 "
                                + "중요한 변경은 서비스 내에서 안내합니다.",
                        INITIAL_EFFECTIVE_AT
                ),
                LegalDocument.create(
                        LegalDocumentType.PRIVACY_POLICY,
                        INITIAL_VERSION,
                        "개인정보 처리방침",
                        "밋플은 회원가입과 서비스 제공을 위해 이메일, 비밀번호의 암호화 값, 닉네임을 "
                                + "필수로 처리하며 프로필 사진과 한줄 소개는 선택적으로 처리합니다. "
                                + "개인정보는 회원 탈퇴 시까지 보관하되 관계 법령에 따라 보존할 의무가 "
                                + "있는 경우 해당 기간 동안 분리 보관합니다. "
                                + "사용자는 자신의 개인정보를 조회·수정하고 회원 탈퇴를 요청할 수 있습니다.",
                        INITIAL_EFFECTIVE_AT
                ),
                LegalDocument.create(
                        LegalDocumentType.PRIVACY_POLICY,
                        CURRENT_PRIVACY_POLICY_VERSION,
                        "개인정보 처리방침",
                        currentPrivacyPolicyContent(),
                        CURRENT_PRIVACY_POLICY_EFFECTIVE_AT
                ),
                LegalDocument.create(
                        LegalDocumentType.AGE_14_CONFIRMATION,
                        INITIAL_VERSION,
                        "만 14세 이상 확인",
                        "회원가입을 진행하는 사용자는 만 14세 이상임을 확인합니다.",
                        INITIAL_EFFECTIVE_AT
                )
        );
    }

    private String currentPrivacyPolicyContent() {
        return """
                밋플 개인정보 처리방침

                시행일: 2026년 9월 12일

                밋플(Meetple, 이하 밋플)은 이용자의 개인정보를 중요하게 여기며 개인정보 보호법 등 관련 법령을 준수합니다.

                1. 처리하는 개인정보와 목적
                - 회원가입·인증: 이메일, 비밀번호 암호화 값, 이메일 인증 시각, 닉네임을 회원 식별, 가입, 로그인, 계정 보호에 이용합니다.
                - 선택 프로필: 프로필 이미지, 한줄 소개, 활동 지역을 프로필 표시와 지역 기반 서비스에 이용합니다.
                - 모임·커뮤니티: 모임 제목·내용·일시·장소·주소·좌표·이미지, 참여 상태와 신청 메시지, 북마크, 채팅 메시지, 알림·읽음·알림 설정을 모임 운영과 이용자 간 소통에 이용합니다.
                - 위치 서비스: 이용자가 위치 권한을 허용하고 주변 모임 기능을 사용할 때 현재 좌표를 1회 조회하고, 입력한 장소 검색어를 처리하여 주변 모임 검색, 지도 표시와 장소·주소 검색에 이용합니다. 현재 위치를 회원 프로필에 상시 저장하거나 백그라운드에서 계속 수집하지 않습니다.
                - 푸시 알림: 앱 설치 식별자, 기기 플랫폼, FCM 등록 토큰, 마지막 갱신 시각을 기기별 알림 발송과 토큰 관리에 이용합니다.
                - 보안·문의 처리: 이메일 인증번호와 일회용 토큰, 요청 IP 주소의 해시 값, 접속·오류 기록을 본인 확인, 비정상 요청 제한, 장애 대응과 보안에 이용할 수 있습니다.
                - 법적 고지: 약관 종류·버전, 확인 또는 동의 여부와 시각을 동의 이력 증명에 이용합니다.

                비밀번호는 원문이 아닌 단방향 암호화 값으로 저장합니다. 밋플은 주민등록번호, 결제정보 등 고유식별정보나 민감정보를 서비스 가입 항목으로 요구하지 않습니다.

                2. 보유 및 이용 기간
                - 회원·프로필·서비스 활동 정보: 회원 탈퇴 시까지 보유합니다.
                - 이메일 인증번호: 기본 5분, 가입·비밀번호 재설정·탈퇴용 일회용 토큰: 기본 15분 동안 보유한 뒤 만료합니다.
                - 로그인 갱신 토큰: 기본 14일 이내이며 로그아웃 또는 탈퇴 시 삭제합니다.
                - 푸시 토큰, 북마크, 알림, 채팅 읽음 상태와 알림 설정: 기기 등록 해제 또는 회원 탈퇴 시 삭제합니다.
                - 운영 로그: 보안 및 장애 대응을 위해 최대 14일 보유합니다.
                - 푸시·이메일 전송 이벤트: 생성 후 기본 7일이 지나면 순차 삭제하고 Kafka 일반·재시도·DLQ 저장소에서는 최대 14일 보유합니다. CDC 복구 안전 조건을 충족하지 못하면 Outbox 정리가 지연될 수 있으며 복구 확인 후 삭제합니다.
                - 데이터베이스 자동 백업: 재해 복구를 위해 최대 35일의 수명주기 안에서 보관한 뒤 자동 삭제합니다.
                - 데이터베이스 최종 스냅샷: 데이터베이스 교체·폐기 시 복구와 장애 조사 목적으로 최대 1년간 별도 보관한 뒤 운영자가 삭제합니다.

                탈퇴하면 이메일·비밀번호·닉네임·소개·지역·프로필 이미지는 삭제하거나 복구할 수 없도록 익명화합니다. 서비스 무결성을 위해 모임과 참여 상태, 채팅의 순서, 약관 확인 시각은 식별정보와 분리된 형태로 남을 수 있으며 주 데이터베이스의 채팅 본문과 참여 신청 메시지는 삭제 또는 대체됩니다. 탈퇴 전에 생성된 푸시·이메일 전송 이벤트의 닉네임과 메시지 본문 사본은 위 이벤트 보유 기간까지 남을 수 있습니다. 관계 법령에 따라 별도 보존이 필요한 정보가 생기는 경우 해당 법령, 항목과 기간을 고지하고 다른 정보와 분리하여 보관합니다.

                3. 개인정보의 제3자 제공
                밋플은 이용자의 개인정보를 원칙적으로 제3자에게 제공하지 않습니다. 이용자의 별도 동의가 있거나 법령에 근거가 있는 경우에만 필요한 범위에서 제공합니다.

                4. 개인정보 처리업무 위탁
                - Amazon Web Services Korea LLC: 서버, 데이터베이스, 캐시, 파일 저장소, 운영 로그 및 이메일 발송 인프라 운영
                - NAVER Cloud Corp.: 지도 표시, 장소 검색, 주소와 좌표 변환
                - Google LLC: Firebase Cloud Messaging을 통한 앱 푸시 알림 전송

                수탁자는 위탁 목적 범위에서만 정보를 처리하며, 밋플은 계약과 서비스 설정을 통해 개인정보 보호 의무를 관리합니다.

                5. 개인정보의 국외 이전
                푸시 알림 기능을 사용하는 경우 FCM 등록 토큰, 기기 플랫폼, 알림 제목·내용, 모임·채팅방·채팅 메시지 식별자, 채팅 순서, 발신 회원 식별자와 닉네임이 암호화된 외부 네트워크를 통해 미국을 포함하여 Google 또는 재위탁자가 처리 시설을 운영하는 국가로 전송될 수 있습니다. 이전은 토큰 등록과 알림 발송 시 발생하며 목적은 푸시 알림 제공입니다. Google의 보유 기간은 Firebase 서비스 정책에 따르며, Firebase 설치 식별자는 고객의 삭제 API 호출 후 실제 및 백업 시스템에서 삭제되기까지 최대 180일이 걸릴 수 있습니다. 회원 탈퇴 또는 토큰 무효화 시 밋플 서버의 FCM 토큰은 삭제됩니다. 푸시 알림은 기기 설정에서 거부할 수 있습니다.

                6. 파기 절차 및 방법
                보유 기간이 끝나거나 처리 목적이 달성된 개인정보는 지체 없이 파기합니다. 전자 파일은 복구하기 어려운 방식으로 삭제하고, 외부 저장소의 프로필 이미지는 삭제 작업을 예약해 제거합니다. 자동 백업은 정해진 수명주기로 삭제하고, 최종 스냅샷은 접근을 제한하여 별도 보관한 뒤 최대 1년 안에 운영자가 삭제합니다.

                7. 이용자의 권리와 행사 방법
                이용자는 앱의 프로필 메뉴에서 개인정보를 조회·수정하고 계정을 탈퇴할 수 있습니다. 앱을 이용할 수 없는 경우 https://api.meetple.shop/account-deletion 에서 이메일 인증 후 삭제를 요청할 수 있습니다. 그 밖의 열람, 정정·삭제, 처리정지 요청과 문의는 meetple99@gmail.com 으로 접수할 수 있으며 밋플은 관련 법령에 따라 지체 없이 처리합니다.

                만 14세 미만은 밋플에 가입할 수 없습니다.

                8. 안전성 확보 조치
                밋플은 비밀번호 단방향 암호화, 이용자와 서비스 및 외부 제공자 사이의 통신 암호화, 내부 시스템의 VPC 보안그룹 접근 제한, 저장장치 암호화, 인증 토큰 만료와 폐기, 비밀정보 분리 보관, 로그 모니터링 등의 보호조치를 적용합니다.

                9. 개인정보 보호 문의
                - 개인정보 처리자 및 담당부서: 밋플 운영팀
                - 서비스명: 밋플(Meetple)
                - 이메일: meetple99@gmail.com

                10. 방침 변경
                이 방침의 내용이 변경되면 시행 전에 앱 또는 공개 웹페이지를 통해 알립니다. 이용자의 권리에 중대한 영향을 주는 변경은 관련 법령이 정한 방법과 기간에 따라 알립니다.

                공고일 및 시행일: 2026년 9월 12일
                """;
    }
}
