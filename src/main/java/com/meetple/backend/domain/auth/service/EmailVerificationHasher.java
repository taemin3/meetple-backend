package com.meetple.backend.domain.auth.service;

import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final EmailVerificationProperties properties;

    public String hashCode(String email, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.hmacSecret().getBytes(StandardCharsets.UTF_8),
                    ALGORITHM
            ));
            byte[] digest = mac.doFinal((email + "\n" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Email verification HMAC is not available.", e);
        }
    }
}
