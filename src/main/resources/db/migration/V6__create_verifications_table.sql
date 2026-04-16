-- Create verifications table to store verification history
CREATE TABLE verifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    signature_id BIGINT NOT NULL,
    user_id BIGINT,
    is_valid BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(500),
    computed_hash VARCHAR(128),
    verified_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_verifications_signature FOREIGN KEY (signature_id) REFERENCES signatures(id) ON DELETE CASCADE,
    CONSTRAINT fk_verifications_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_verifications_signature_id ON verifications(signature_id);
CREATE INDEX idx_verifications_user_id ON verifications(user_id);
CREATE INDEX idx_verifications_verified_at ON verifications(verified_at);
