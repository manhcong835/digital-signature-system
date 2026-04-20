-- Add X.509 certificate columns to keys table for PAdES support.
-- Each key gets a cert issued by the local self-signed CA. Existing keys
-- are backfilled on first boot by LegacyKeyBackfillRunner.
ALTER TABLE `keys`
    ADD COLUMN certificate TEXT NULL,
    ADD COLUMN cert_serial_number VARCHAR(64) NULL,
    ADD COLUMN cert_issued_at DATETIME NULL,
    ADD COLUMN cert_expires_at DATETIME NULL;

CREATE INDEX idx_keys_cert_serial ON `keys`(cert_serial_number);
