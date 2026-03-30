package syber.security.digitalsignature.key.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import syber.security.digitalsignature.key.entity.KeyPair;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyResponse {

    private Long id;
    private String name;
    private String publicKey;
    private String algorithm;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
    private Boolean revoked;
    private String certSerialNumber;
    private LocalDateTime certIssuedAt;
    private LocalDateTime certExpiresAt;
    private Boolean hasCertificate;

    public static KeyResponse from(KeyPair key) {
        return KeyResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .publicKey(key.getPublicKey())
                .algorithm(key.getAlgorithm())
                .isDefault(key.getIsDefault())
                .createdAt(key.getCreatedAt())
                .revokedAt(key.getRevokedAt())
                .revoked(key.isRevoked())
                .certSerialNumber(key.getCertSerialNumber())
                .certIssuedAt(key.getCertIssuedAt())
                .certExpiresAt(key.getCertExpiresAt())
                .hasCertificate(key.getCertificate() != null)
                .build();
    }

    public static KeyResponse summary(KeyPair key) {
        return KeyResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .algorithm(key.getAlgorithm())
                .isDefault(key.getIsDefault())
                .createdAt(key.getCreatedAt())
                .revokedAt(key.getRevokedAt())
                .revoked(key.isRevoked())
                .certSerialNumber(key.getCertSerialNumber())
                .certIssuedAt(key.getCertIssuedAt())
                .certExpiresAt(key.getCertExpiresAt())
                .hasCertificate(key.getCertificate() != null)
                .build();
    }
}
