CREATE TABLE social_statistics_tasks (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 account_id BIGINT NOT NULL,
 -- Zero denotes account scope; positive IDs refer to social_content in this account.
 content_id BIGINT NOT NULL DEFAULT 0,
 operation VARCHAR(191) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 due_at DATETIME(6) NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 last_attempt_at DATETIME(6), last_success_at DATETIME(6), error_message VARCHAR(255),
 page_cursor TEXT, progress JSON, seen_cursors JSON,
 window_from DATE, window_to DATE,
 result JSON,
 UNIQUE KEY uq_statistics_task(account_id,content_id,operation),
 KEY ix_statistics_due(account_id,enabled,due_at),
 FOREIGN KEY(account_id) REFERENCES social_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE social_statistics_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 task_id BIGINT NOT NULL,
 snapshot_bucket BIGINT NOT NULL,
 observed_at DATETIME(6) NOT NULL,
 window_from DATE NOT NULL, window_to DATE NOT NULL,
 result JSON NOT NULL,
 UNIQUE KEY uq_statistics_snapshot(task_id,snapshot_bucket),
 FOREIGN KEY(task_id) REFERENCES social_statistics_tasks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
