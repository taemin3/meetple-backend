DROP INDEX IF EXISTS idx_meetings_location_column_gist;
DROP INDEX IF EXISTS idx_meetings_location_gist;

CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE meetings
    ADD COLUMN location geography(Point, 4326)
    GENERATED ALWAYS AS (
        ST_SetSRID(
            ST_MakePoint(
                longitude::double precision,
                latitude::double precision
            ),
            4326
        )::geography
    ) STORED;

CREATE INDEX idx_meetings_location_gist
    ON meetings
    USING GIST (location);
