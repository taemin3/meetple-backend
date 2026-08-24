CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    default_image_url VARCHAR(2048),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS members (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(255),
    region VARCHAR(100),
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_members_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS meetings (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    location_name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(10, 6) NOT NULL,
    max_people INTEGER NOT NULL,
    current_people INTEGER NOT NULL,
    meeting_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    cancel_reason VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    thumbnail_image_url VARCHAR(2048),
    host_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_meetings_host FOREIGN KEY (host_id) REFERENCES members (id),
    CONSTRAINT fk_meetings_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE INDEX IF NOT EXISTS idx_meetings_host_id
    ON meetings (host_id);
CREATE INDEX IF NOT EXISTS idx_meetings_category_id
    ON meetings (category_id);
CREATE INDEX IF NOT EXISTS idx_meetings_status_meeting_date
    ON meetings (status, meeting_date);

CREATE TABLE IF NOT EXISTS meeting_participations (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    message TEXT,
    reviewed_at TIMESTAMP,
    canceled_at TIMESTAMP,
    meeting_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_meeting_participations_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE,
    CONSTRAINT fk_meeting_participations_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uk_meeting_participations_meeting_member
        UNIQUE (meeting_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_meeting_participations_meeting_id
    ON meeting_participations (meeting_id);
CREATE INDEX IF NOT EXISTS idx_meeting_participations_member_id
    ON meeting_participations (member_id);
CREATE INDEX IF NOT EXISTS idx_meeting_participations_status
    ON meeting_participations (status);

CREATE TABLE IF NOT EXISTS meeting_bookmarks (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_meeting_bookmarks_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE,
    CONSTRAINT fk_meeting_bookmarks_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uk_meeting_bookmarks_meeting_member
        UNIQUE (meeting_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_meeting_bookmarks_member_id
    ON meeting_bookmarks (member_id);
CREATE INDEX IF NOT EXISTS idx_meeting_bookmarks_meeting_id
    ON meeting_bookmarks (meeting_id);

CREATE TABLE IF NOT EXISTS meeting_images (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_meeting_images_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE,
    CONSTRAINT uk_meeting_images_meeting_sort_order
        UNIQUE (meeting_id, sort_order)
);

CREATE INDEX IF NOT EXISTS idx_meeting_images_meeting_id
    ON meeting_images (meeting_id);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    meeting_id BIGINT,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_notifications_member
        FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE INDEX IF NOT EXISTS idx_notifications_member_created_at
    ON notifications (member_id, created_at);
