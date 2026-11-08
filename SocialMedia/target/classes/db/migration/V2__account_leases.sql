CREATE TABLE social_account_leases (
 account_id BIGINT PRIMARY KEY, owner VARCHAR(36), expires_at DATETIME(6) NOT NULL,
 FOREIGN KEY(account_id) REFERENCES social_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
