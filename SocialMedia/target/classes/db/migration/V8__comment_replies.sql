CREATE TABLE social_comment_replies (
 id VARCHAR(36) PRIMARY KEY, comment_id BIGINT NOT NULL, request_key VARCHAR(128) NOT NULL,
 reply_text LONGTEXT NOT NULL, actor VARCHAR(191) NOT NULL, status VARCHAR(32) NOT NULL,
 external_reply_id VARCHAR(191), reconciled_by VARCHAR(191), error_message VARCHAR(255), attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6), started_at DATETIME(6), created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_reply_request(actor,request_key), KEY ix_reply_queue(status,created_at),
 FOREIGN KEY(comment_id) REFERENCES social_comments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
ALTER TABLE social_comments ADD COLUMN resolved_at DATETIME(6), ADD COLUMN resolved_by VARCHAR(191);
