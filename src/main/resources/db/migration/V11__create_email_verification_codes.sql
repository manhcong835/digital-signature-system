-- Unified table for both registration OTP and login 2FA codes
CREATE TABLE email_verification_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    challenge_token VARCHAR(64) NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evc_email_type (email, type),
    INDEX idx_evc_challenge (challenge_token),
    INDEX idx_evc_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
