package syber.security.digitalsignature.signature.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import syber.security.digitalsignature.document.entity.Document;
import syber.security.digitalsignature.key.entity.KeyPair;
import syber.security.digitalsignature.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "signatures")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "key_id")
    private KeyPair keyPair;

    @Column(name = "signature_value", nullable = false, columnDefinition = "TEXT")
    private String signatureValue;

    @Column(nullable = false, length = 50)
    private String algorithm;

    @Column(name = "document_hash", nullable = false, length = 128)
    private String documentHash;

    @CreationTimestamp
    @Column(name = "signed_at", nullable = false, updatable = false)
    private LocalDateTime signedAt;

    @Column(name = "verify_status", length = 20)
    private String verifyStatus;

    @Column(name = "signing_cert_pem", columnDefinition = "TEXT")
    private String signingCertPem;

    @Column(name = "pades_pdf_path", length = 500)
    private String padesPdfPath;

    @Column(name = "signature_page")
    private Integer signaturePage;

    @Column(name = "sig_rect_x")
    private Double sigRectX;

    @Column(name = "sig_rect_y")
    private Double sigRectY;

    @Column(name = "sig_rect_w")
    private Double sigRectW;

    @Column(name = "sig_rect_h")
    private Double sigRectH;
}
