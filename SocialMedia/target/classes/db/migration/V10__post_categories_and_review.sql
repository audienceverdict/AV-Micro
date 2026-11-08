ALTER TABLE application_content
 ADD COLUMN topic VARCHAR(16),
 ADD COLUMN category_name VARCHAR(255);

CREATE TABLE social_post_reviews (
 content_id BIGINT PRIMARY KEY,
 status VARCHAR(16) NOT NULL,
 topic VARCHAR(16) NOT NULL,
 category_name VARCHAR(255) NOT NULL,
 reviewed_by VARCHAR(191) NOT NULL,
 reviewed_at DATETIME(6) NOT NULL,
 FOREIGN KEY(content_id) REFERENCES social_content(id),
 KEY ix_review_status(status,content_id),
 KEY ix_review_category(topic,category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
