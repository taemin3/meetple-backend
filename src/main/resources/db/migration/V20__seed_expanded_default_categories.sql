INSERT INTO categories (name, default_image_url, created_at, updated_at)
SELECT category_name, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('친목'),
    ('여행'),
    ('맛집'),
    ('비즈니스'),
    ('반려동물')
) AS default_categories(category_name)
WHERE NOT EXISTS (
    SELECT 1
    FROM categories
    WHERE categories.name = default_categories.category_name
);
