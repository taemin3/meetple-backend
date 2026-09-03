CREATE EXTENSION IF NOT EXISTS pg_bigm;

CREATE INDEX idx_meetings_title_bigm
    ON meetings
    USING gin (lower(title) gin_bigm_ops);

CREATE INDEX idx_meetings_location_name_bigm
    ON meetings
    USING gin (lower(location_name) gin_bigm_ops);

CREATE INDEX idx_meetings_address_bigm
    ON meetings
    USING gin (lower(address) gin_bigm_ops);

ANALYZE meetings;