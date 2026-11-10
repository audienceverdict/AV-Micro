-- Apply once to an existing database before deploying the updated identity-service.
-- Back up the database and verify against the deployed users schema first.
ALTER TABLE users MODIFY COLUMN mobile VARCHAR(20) NULL;
ALTER TABLE users ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
