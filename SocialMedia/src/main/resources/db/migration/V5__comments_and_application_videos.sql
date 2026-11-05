CREATE TABLE application_videos (
 id VARCHAR(64) PRIMARY KEY, title VARCHAR(255) NOT NULL, description TEXT,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE application_video_uploads (
 content_id BIGINT PRIMARY KEY, video_id VARCHAR(64) NOT NULL, assigned_at DATETIME(6) NOT NULL,
 FOREIGN KEY(content_id) REFERENCES social_content(id), FOREIGN KEY(video_id) REFERENCES application_videos(id),
 KEY ix_video_uploads(video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE application_video_assignment_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, content_id BIGINT NOT NULL,
 old_video_id VARCHAR(64), new_video_id VARCHAR(64), changed_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE social_comments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, content_id BIGINT NOT NULL,
 external_comment_id VARCHAR(191) NOT NULL, parent_external_id VARCHAR(191),
 author_id VARCHAR(255), author_name VARCHAR(255), comment_text LONGTEXT,
 published_at DATETIME(6), provider_updated_at DATETIME(6), retrieved_at DATETIME(6) NOT NULL,
 like_count BIGINT, reply_count BIGINT,
 UNIQUE KEY uq_comment(content_id,external_comment_id), KEY ix_comments_parent(content_id,parent_external_id),
 FOREIGN KEY(content_id) REFERENCES social_content(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE social_comment_tasks (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, content_id BIGINT NOT NULL, parent_external_id VARCHAR(191) NOT NULL DEFAULT '',
 page_cursor TEXT, due_at DATETIME(6) NOT NULL, last_success_at DATETIME(6), status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 error_message VARCHAR(255), pages_fetched BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uq_comment_task(content_id,parent_external_id), KEY ix_comment_due(due_at),
 FOREIGN KEY(content_id) REFERENCES social_content(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
