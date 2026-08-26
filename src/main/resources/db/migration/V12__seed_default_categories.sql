INSERT INTO categories (name, default_image_url, created_at, updated_at)
SELECT '운동', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '운동');

INSERT INTO categories (name, default_image_url, created_at, updated_at)
SELECT '스터디', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '스터디');

INSERT INTO categories (name, default_image_url, created_at, updated_at)
SELECT '취미', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '취미');
