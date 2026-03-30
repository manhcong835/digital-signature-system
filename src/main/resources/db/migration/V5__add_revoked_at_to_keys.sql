-- Add revoked_at column to keys table for key revocation support
ALTER TABLE `keys` ADD COLUMN revoked_at DATETIME NULL DEFAULT NULL;
CREATE INDEX idx_keys_revoked_at ON `keys`(revoked_at);
