package com.meetple.backend.domain.image.storage;

public interface ImageStorageClient {

    PresignedImageUpload createPresignedUpload(ImageUploadObject uploadObject);
}
