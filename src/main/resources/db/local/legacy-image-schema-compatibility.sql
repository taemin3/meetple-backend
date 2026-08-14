DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'meeting_images'
          AND column_name = 'image_url'
    ) THEN
        ALTER TABLE meeting_images
            ALTER COLUMN image_url DROP NOT NULL;
    END IF;
END
$$;
