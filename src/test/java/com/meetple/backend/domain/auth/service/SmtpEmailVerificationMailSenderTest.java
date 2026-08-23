package com.meetple.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.mail.SmtpEmailVerificationMailSender;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailVerificationMailSenderTest {

    @Mock
    private JavaMailSender javaMailSender;

    private SmtpEmailVerificationMailSender mailSender;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties properties = new EmailVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                Duration.ofMinutes(15),
                5,
                Duration.ofMinutes(1),
                5,
                Duration.ofMinutes(1),
                100,
                Duration.ofMinutes(1),
                10,
                "test-email-verification-secret-1234567890",
                "noreply@meetple.test"
        );
        mailSender = new SmtpEmailVerificationMailSender(javaMailSender, properties);
    }

    @Test
    void sendVerificationCodeBuildsSignupEmail() {
        mailSender.sendVerificationCode(
                "user@meetple.com",
                "123456",
                Duration.ofMinutes(5)
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(
                SimpleMailMessage.class
        );
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("noreply@meetple.test");
        assertThat(message.getTo()).containsExactly("user@meetple.com");
        assertThat(message.getText()).contains("123456", "5분");
    }

    @Test
    void sendVerificationCodeMapsMailFailure() {
        doThrow(new MailSendException("smtp unavailable"))
                .when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> mailSender.sendVerificationCode(
                "user@meetple.com",
                "123456",
                Duration.ofMinutes(5)
        ))
                .isInstanceOf(BaseException.class)
                .hasMessage("인증 메일 발송에 실패했습니다.")
                .extracting(exception -> ((BaseException) exception).getErrorStatus())
                .isEqualTo(ErrorStatus.EXTERNAL_API_ERROR);
    }

    @Test
    void sendPasswordResetCodeBuildsPasswordResetEmail() {
        mailSender.sendPasswordResetCode(
                "user@meetple.com",
                "654321",
                Duration.ofMinutes(5)
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(
                SimpleMailMessage.class
        );
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getSubject()).contains("비밀번호 재설정");
        assertThat(message.getText()).contains("비밀번호 재설정", "654321", "5분");
    }

    @Test
    void emailShowsActualRemainingChallengeTime() {
        mailSender.sendVerificationCode(
                "user@meetple.com",
                "123456",
                Duration.ofSeconds(150)
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(
                SimpleMailMessage.class
        );
        verify(javaMailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("2분 30초 동안 유효");
    }
}
