package com.meetple.backend.domain.image.storage;

import com.meetple.backend.domain.image.config.ImageStorageProperties;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class S3ImageStorageClient implements ImageStorageClient {

    private final ImageStorageProperties properties;

    @Override
    public PresignedImageUpload createPresignedUpload(ImageUploadObject uploadObject) {
        validateConfiguration();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(uploadObject.objectKey())
                .contentType(uploadObject.contentType())
                .contentLength(uploadObject.contentLength())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(uploadObject.expiresIn())
                .putObjectRequest(putObjectRequest)
                .build();

        try (S3Presigner presigner = createPresigner()) {
            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
            return new PresignedImageUpload(
                    presignedRequest.url().toString(),
                    buildFileUrl(uploadObject.objectKey()),
                    uploadObject.objectKey(),
                    "PUT",
                    extractHeaders(presignedRequest),
                    uploadObject.expiresIn()
            );
        }
    }

    private Map<String, String> extractHeaders(PresignedPutObjectRequest presignedRequest) {
        Map<String, String> headers = new LinkedHashMap<>();
        presignedRequest.httpRequest().headers().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host") && !values.isEmpty()) {
                headers.put(name, String.join(",", values));
            }
        });
        return headers;
    }

    private S3Presigner createPresigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccessEnabled())
                        .build());

        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }

        return builder.build();
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.bucket())
                || !StringUtils.hasText(properties.region())
                || !StringUtils.hasText(properties.accessKey())
                || !StringUtils.hasText(properties.secretKey())) {
            throw new BaseException(ErrorStatus.EXTERNAL_API_ERROR, "이미지 저장소 설정이 누락되었습니다.");
        }
    }

    private String buildFileUrl(String objectKey) {
        if (StringUtils.hasText(properties.publicBaseUrl())) {
            return joinUrl(properties.publicBaseUrl(), objectKey);
        }
        if (StringUtils.hasText(properties.endpoint())) {
            return joinUrl(properties.endpoint(), properties.bucket(), objectKey);
        }
        return "https://" + properties.bucket() + ".s3." + properties.region()
                + ".amazonaws.com/" + objectKey;
    }

    private String joinUrl(String baseUrl, String... paths) {
        String result = stripTrailingSlash(baseUrl);
        for (String path : paths) {
            result += "/" + stripSlashes(path);
        }
        return result;
    }

    private String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private String stripSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
