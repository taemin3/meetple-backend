ALTER TABLE members
    ADD COLUMN IF NOT EXISTS profile_image_object_key VARCHAR(255);

ALTER TABLE meeting_images
    ADD COLUMN IF NOT EXISTS object_key VARCHAR(255);

ALTER TABLE meeting_images
    ALTER COLUMN image_url DROP NOT NULL;

ALTER TABLE meeting_images
    ADD CONSTRAINT chk_meeting_images_storage_reference
        CHECK (object_key IS NOT NULL OR image_url IS NOT NULL);
