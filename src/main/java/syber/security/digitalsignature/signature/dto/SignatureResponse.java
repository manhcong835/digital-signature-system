package syber.security.digitalsignature.signature.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import syber.security.digitalsignature.signature.entity.Signature;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureResponse {

    private Long id;
    private Long documentId;
    private String documentName;
    private Long keyId;
    private String keyName;
    private String signerEmail;
    private String signerName;
    private String signatureValue;
    private String algorithm;
    private String documentHash;
    private LocalDateTime signedAt;
    private String verifyStatus;
    private Boolean padesPdfAvailable;
    private Integer signaturePage;
    private Double sigRectX;
    private Double sigRectY;
    private Double sigRectW;
    private Double sigRectH;

    public static SignatureResponse from(Signature s) {
        return SignatureResponse.builder()
                .id(s.getId())
                .documentId(s.getDocument() != null ? s.getDocument().getId() : null)
                .documentName(s.getDocument() != null ? s.getDocument().getOriginalName() : null)
                .keyId(s.getKeyPair() != null ? s.getKeyPair().getId() : null)
                .keyName(s.getKeyPair() != null ? s.getKeyPair().getName() : null)
                .signerEmail(s.getUser() != null ? s.getUser().getEmail() : null)
                .signerName(s.getUser() != null ? s.getUser().getFullName() : null)
                .signatureValue(s.getSignatureValue())
                .algorithm(s.getAlgorithm())
                .documentHash(s.getDocumentHash())
                .signedAt(s.getSignedAt())
                .verifyStatus(s.getVerifyStatus())
                .padesPdfAvailable(s.getPadesPdfPath() != null)
                .signaturePage(s.getSignaturePage())
                .sigRectX(s.getSigRectX())
                .sigRectY(s.getSigRectY())
                .sigRectW(s.getSigRectW())
                .sigRectH(s.getSigRectH())
                .build();
    }

    public static SignatureResponse summary(Signature s) {
        return SignatureResponse.builder()
                .id(s.getId())
                .documentId(s.getDocument() != null ? s.getDocument().getId() : null)
                .documentName(s.getDocument() != null ? s.getDocument().getOriginalName() : null)
                .keyId(s.getKeyPair() != null ? s.getKeyPair().getId() : null)
                .keyName(s.getKeyPair() != null ? s.getKeyPair().getName() : null)
                .signerEmail(s.getUser() != null ? s.getUser().getEmail() : null)
                .signerName(s.getUser() != null ? s.getUser().getFullName() : null)
                .algorithm(s.getAlgorithm())
                .documentHash(s.getDocumentHash())
                .signedAt(s.getSignedAt())
                .verifyStatus(s.getVerifyStatus())
                .padesPdfAvailable(s.getPadesPdfPath() != null)
                .signaturePage(s.getSignaturePage())
                .sigRectX(s.getSigRectX())
                .sigRectY(s.getSigRectY())
                .sigRectW(s.getSigRectW())
                .sigRectH(s.getSigRectH())
                .build();
    }
}
