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
    public void sendVerificationCode(String recipient, String code, Duration expiresIn) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(recipient);
        message.setSubject("[밋플] 이메일 인증번호 안내");
        message.setText("""
                밋플 회원가입 이메일 인증번호입니다.

                인증번호: %s

                인증번호는 %d분 동안 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(code, expiresIn.toMinutes()));

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "인증 메일 발송에 실패했습니다.");
        }
    }
}
