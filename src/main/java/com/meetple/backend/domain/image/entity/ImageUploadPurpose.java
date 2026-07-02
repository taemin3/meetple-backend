package com.meetple.backend.domain.image.entity;

public enum ImageUploadPurpose {
    PROFILE("profile"),
    MEETING("meeting");

    private final String pathSegment;

    ImageUploadPurpose(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    public String pathSegment() {
        return pathSegment;
    }
}
