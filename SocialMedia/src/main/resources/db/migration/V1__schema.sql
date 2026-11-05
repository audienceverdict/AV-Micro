CREATE TABLE social_accounts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, platform VARCHAR(32) NOT NULL, external_account_id VARCHAR(191) NOT NULL,
 account_name VARCHAR(255), enabled BIT NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_account(platform,external_account_id), KEY ix_enabled(enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE social_credentials (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL, credential_reference VARCHAR(255) NOT NULL,
 expires_at DATETIME(6), created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_credential(account_id), FOREIGN KEY(account_id) REFERENCES social_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE social_content (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL, platform VARCHAR(32) NOT NULL, external_content_id VARCHAR(191) NOT NULL,
 content_type VARCHAR(32), provider_content_type VARCHAR(64), author_id VARCHAR(255), author_name VARCHAR(255),
 title TEXT, caption LONGTEXT, permalink TEXT, thumbnail_url TEXT, published_at DATETIME(6), provider_updated_at DATETIME(6), retrieved_at DATETIME(6),
 engagement_metadata JSON, provider_metadata JSON, content_status VARCHAR(32), fingerprint VARCHAR(64),
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_content(platform,account_id,external_content_id), KEY ix_published(account_id,published_at),
 FOREIGN KEY(account_id) REFERENCES social_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE social_media_assets (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, content_id BIGINT NOT NULL, external_asset_id VARCHAR(191) NOT NULL, media_type VARCHAR(32),
 media_url TEXT, thumbnail_url TEXT, width INT, height INT, duration_seconds DECIMAL(18,6), display_order INT NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_asset(content_id,external_asset_id), FOREIGN KEY(content_id) REFERENCES social_content(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE social_sync_checkpoints (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL, sync_mode VARCHAR(32) NOT NULL,
 last_successful_sync_at DATETIME(6), provider_high_watermark TEXT, backfill_cursor TEXT, backfill_completed BIT NOT NULL,
 last_attempt_at DATETIME(6), last_status VARCHAR(32), window_from DATETIME(6), window_to DATETIME(6), window_cursor TEXT,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_checkpoint(account_id,sync_mode), FOREIGN KEY(account_id) REFERENCES social_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE social_sync_jobs (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL, sync_mode VARCHAR(32), started_at DATETIME(6), completed_at DATETIME(6), status VARCHAR(32),
 fetched_count BIGINT NOT NULL, inserted_count BIGINT NOT NULL, updated_count BIGINT NOT NULL, skipped_count BIGINT NOT NULL, failed_count BIGINT NOT NULL,
 error_message TEXT, KEY ix_job(account_id,started_at), FOREIGN KEY(account_id) REFERENCES social_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
