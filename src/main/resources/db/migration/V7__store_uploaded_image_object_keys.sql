ALTER TABLE members
    ADD COLUMN profile_image_object_key VARCHAR(255);

ALTER TABLE meetings
    ADD COLUMN thumbnail_image_object_key VARCHAR(255);

ALTER TABLE meeting_images
    ADD COLUMN object_key VARCHAR(255);

ALTER TABLE meeting_images
    ALTER COLUMN image_url DROP NOT NULL;
