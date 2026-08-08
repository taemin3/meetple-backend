package com.meetple.backend.domain.push.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(PushFcmProperties.class)
@ConditionalOnProperty(prefix = "push.fcm", name = "enabled", havingValue = "true")
public class FirebaseAdminConfig {

    @Bean(destroyMethod = "delete")
    public FirebaseApp pushFirebaseApp(PushFcmProperties properties) throws IOException {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(loadCredentials(properties))
                .build();
        return FirebaseApp.initializeApp(options, "meetple-push");
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp pushFirebaseApp) {
        return FirebaseMessaging.getInstance(pushFirebaseApp);
    }

    private GoogleCredentials loadCredentials(PushFcmProperties properties) throws IOException {
        if (!StringUtils.hasText(properties.credentialsPath())) {
            return GoogleCredentials.getApplicationDefault();
        }
        try (InputStream credentials = Files.newInputStream(Path.of(properties.credentialsPath()))) {
            return GoogleCredentials.fromStream(credentials);
        }
    }
}
