ALTER TABLE social_sync_checkpoints ADD COLUMN last_reconciled_at DATETIME(6), ADD COLUMN window_reconciliation BIT NOT NULL DEFAULT 0;
