package com.meetple.backend.domain.auth.mail;

import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpEmailVerificationMailSender implements EmailVerificationMailSender {

    private final JavaMailSender javaMailSender;
    private final EmailVerificationProperties properties;

    @Override
    public void sendVerificationCode(String recipient, String code) {
        send(
                recipient,
                "[밋플] 이메일 인증번호 안내",
                "밋플 회원가입 이메일 인증번호입니다.",
                code
        );
    }

    @Override
    public void sendPasswordResetCode(String recipient, String code) {
        send(
                recipient,
                "[밋플] 비밀번호 재설정 인증번호 안내",
                "밋플 비밀번호 재설정 인증번호입니다.",
                code
        );
    }

    private void send(
            String recipient,
            String subject,
            String introduction,
            String code
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText("""
                %s

                인증번호: %s

                인증번호는 발급 후 %s 동안 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(introduction, code, formatDuration(properties.codeTtl())));

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "인증 메일 발송에 실패했습니다.");
        }
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = Math.max(1, duration.toSeconds());
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes == 0) {
            return seconds + "초";
        }
        if (seconds == 0) {
            return minutes + "분";
        }
        return minutes + "분 " + seconds + "초";
    }
}
