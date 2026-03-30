-- Create keys table (`keys` is a reserved word in MySQL, escaped with backticks)
CREATE TABLE `keys` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    public_key TEXT NOT NULL,
    encrypted_private_key TEXT NOT NULL,
    algorithm VARCHAR(50) DEFAULT 'SHA512withRSA',
    is_default BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_keys_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create indexes
CREATE INDEX idx_keys_user_id ON `keys`(user_id);
CREATE INDEX idx_keys_is_default ON `keys`(user_id, is_default);
