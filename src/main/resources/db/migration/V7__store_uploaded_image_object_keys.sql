ALTER TABLE members
    ADD COLUMN profile_image_object_key VARCHAR(255);

UPDATE members
SET profile_image_object_key = SUBSTRING(
        profile_image_url FROM POSITION('images/profile/' IN profile_image_url)
    )
WHERE profile_image_url IS NOT NULL
  AND POSITION('images/profile/' IN profile_image_url) > 0;

ALTER TABLE meetings
    ADD COLUMN thumbnail_image_object_key VARCHAR(255);

UPDATE meetings
SET thumbnail_image_object_key = SUBSTRING(
        thumbnail_image_url FROM POSITION('images/meeting/' IN thumbnail_image_url)
    )
WHERE thumbnail_image_url IS NOT NULL
  AND POSITION('images/meeting/' IN thumbnail_image_url) > 0;

ALTER TABLE meeting_images
    ADD COLUMN object_key VARCHAR(255);

UPDATE meeting_images
SET object_key = SUBSTRING(image_url FROM POSITION('images/meeting/' IN image_url))
WHERE image_url IS NOT NULL
  AND POSITION('images/meeting/' IN image_url) > 0;

ALTER TABLE meeting_images
    ALTER COLUMN image_url DROP NOT NULL;
