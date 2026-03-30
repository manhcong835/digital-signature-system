package syber.security.digitalsignature.key.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import syber.security.digitalsignature.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "`keys`")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @Column(length = 50)
    private String algorithm = "SHA512withRSA";

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(columnDefinition = "TEXT")
    private String certificate;

    @Column(name = "cert_serial_number", length = 64)
    private String certSerialNumber;

    @Column(name = "cert_issued_at")
    private LocalDateTime certIssuedAt;

    @Column(name = "cert_expires_at")
    private LocalDateTime certExpiresAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
