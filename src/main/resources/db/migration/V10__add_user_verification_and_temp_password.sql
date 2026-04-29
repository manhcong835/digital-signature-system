-- Add email verification and temporary password support to users table
ALTER TABLE users
    ADD COLUMN is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN temp_password_hash VARCHAR(255) NULL,
    ADD COLUMN temp_password_expires_at DATETIME NULL,
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Pre-existing users are grandfathered as verified so they don't get locked out
UPDATE users SET is_email_verified = TRUE;
