package com.meetple.backend.domain.push.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirebaseAdminConfigTest {

    private static final String AUTHORIZED_USER_JSON = """
            {
              "type": "authorized_user",
              "client_id": "test-client",
              "client_secret": "test-secret",
              "refresh_token": "test-refresh-token"
            }
            """;

    private final FirebaseAdminConfig config = new FirebaseAdminConfig();

    @Test
    void loadsCredentialsFromInjectedJsonBeforeFilePath(@TempDir Path tempDir) throws Exception {
        Path invalidFile = tempDir.resolve("invalid.json");
        Files.writeString(invalidFile, "not-json");
        PushFcmProperties properties = new PushFcmProperties(
                true,
                invalidFile.toString(),
                AUTHORIZED_USER_JSON
        );

        GoogleCredentials credentials = config.loadCredentials(properties);

        assertInstanceOf(UserCredentials.class, credentials);
    }

    @Test
    void keepsLocalCredentialFileSupport(@TempDir Path tempDir) throws Exception {
        Path credentialsFile = tempDir.resolve("firebase.json");
        Files.writeString(credentialsFile, AUTHORIZED_USER_JSON);
        PushFcmProperties properties = new PushFcmProperties(
                true,
                credentialsFile.toString(),
                ""
        );

        GoogleCredentials credentials = config.loadCredentials(properties);

        assertInstanceOf(UserCredentials.class, credentials);
    }
}
