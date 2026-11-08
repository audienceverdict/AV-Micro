CREATE TABLE application_content (
 id VARCHAR(64) PRIMARY KEY,
 request_key VARCHAR(128) NOT NULL,
 title VARCHAR(255) NOT NULL,
 caption TEXT NOT NULL,
 content_type VARCHAR(16) NOT NULL,
 media_type VARCHAR(64),
 media_size BIGINT NOT NULL DEFAULT 0,
 youtube_privacy VARCHAR(16) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_content_request(request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE social_publications (
 id VARCHAR(64) PRIMARY KEY,
 content_id VARCHAR(64) NOT NULL,
 platform VARCHAR(32) NOT NULL,
 external_account_id VARCHAR(191) NOT NULL,
 status VARCHAR(32) NOT NULL,
 resume_status VARCHAR(32),
 remote_reference TEXT,
 external_content_id VARCHAR(191),
 permalink TEXT,
 error_message VARCHAR(255),
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NOT NULL,
 deadline_at DATETIME(6) NOT NULL,
 started_at DATETIME(6),
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_publication(content_id,platform,external_account_id),
 KEY ix_publication_due(status,next_attempt_at),
 FOREIGN KEY(content_id) REFERENCES application_content(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
