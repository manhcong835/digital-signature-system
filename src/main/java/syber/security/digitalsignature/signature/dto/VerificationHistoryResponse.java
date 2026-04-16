package syber.security.digitalsignature.signature.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import syber.security.digitalsignature.signature.entity.Verification;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationHistoryResponse {

    private Long id;
    private Long signatureId;
    private String signerName;
    private boolean valid;
    private String status;
    private String message;
    private String computedHash;
    private LocalDateTime verifiedAt;
    private String verifierEmail;

    public static VerificationHistoryResponse from(Verification v) {
        return VerificationHistoryResponse.builder()
                .id(v.getId())
                .signatureId(v.getSignature().getId())
                .signerName(v.getSignature().getUser() != null ? v.getSignature().getUser().getFullName() : null)
                .valid(v.getIsValid())
                .status(v.getStatus())
                .message(v.getMessage())
                .computedHash(v.getComputedHash())
                .verifiedAt(v.getVerifiedAt())
                .verifierEmail(v.getUser() != null ? v.getUser().getEmail() : null)
                .build();
    }
}
