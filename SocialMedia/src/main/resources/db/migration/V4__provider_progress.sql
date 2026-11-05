ALTER TABLE social_sync_checkpoints ADD COLUMN retry_not_before DATETIME(6), ADD COLUMN ephemeral_cursor TEXT;
