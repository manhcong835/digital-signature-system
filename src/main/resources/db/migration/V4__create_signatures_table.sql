-- Create signatures table
CREATE TABLE signatures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    user_id BIGINT,
    key_id BIGINT,
    signature_value TEXT NOT NULL,
    algorithm VARCHAR(50) NOT NULL,
    document_hash VARCHAR(128) NOT NULL,
    signed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    verify_status VARCHAR(20),
    CONSTRAINT fk_signatures_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_signatures_user     FOREIGN KEY (user_id)     REFERENCES users(id),
    CONSTRAINT fk_signatures_key      FOREIGN KEY (key_id)      REFERENCES `keys`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create indexes
CREATE INDEX idx_signatures_document_id ON signatures(document_id);
CREATE INDEX idx_signatures_user_id ON signatures(user_id);
CREATE INDEX idx_signatures_signed_at ON signatures(signed_at);
