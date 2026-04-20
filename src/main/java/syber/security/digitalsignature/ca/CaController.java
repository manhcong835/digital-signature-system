package syber.security.digitalsignature.ca;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import syber.security.digitalsignature.key.entity.KeyPair;
import syber.security.digitalsignature.key.repository.KeyPairRepository;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ca")
@RequiredArgsConstructor
@Tag(name = "CA", description = "Certificate authority APIs (public)")
public class CaController {

    private final CaService caService;
    private final KeyPairRepository keyPairRepository;

    @GetMapping(value = "/certificate", produces = "application/x-pem-file")
    @Operation(summary = "Download CA certificate (PEM)")
    public ResponseEntity<byte[]> downloadCaCertificatePem() throws Exception {
        X509Certificate cert = caService.getCaCertificate();
        String pem = toPem(cert);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ca.pem\"")
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .body(pem.getBytes());
    }

    @GetMapping(value = "/certificate.cer", produces = "application/pkix-cert")
    @Operation(summary = "Download CA certificate (DER .cer for Adobe import)")
    public ResponseEntity<byte[]> downloadCaCertificateDer() throws Exception {
        X509Certificate cert = caService.getCaCertificate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ca.cer\"")
                .contentType(MediaType.parseMediaType("application/pkix-cert"))
                .body(cert.getEncoded());
    }

    @GetMapping(value = "/crl", produces = "application/pkix-crl")
    @Operation(summary = "Download current CRL")
    public ResponseEntity<byte[]> downloadCrl() throws Exception {
        List<CaService.RevokedEntry> entries = collectRevokedEntries();
        X509CRL crl = caService.buildCrl(entries);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ca.crl\"")
                .contentType(MediaType.parseMediaType("application/pkix-crl"))
                .body(crl.getEncoded());
    }

    private List<CaService.RevokedEntry> collectRevokedEntries() {
        List<KeyPair> revoked = keyPairRepository.findAll().stream()
                .filter(k -> k.isRevoked() && k.getCertificate() != null && k.getCertSerialNumber() != null)
                .toList();
        List<CaService.RevokedEntry> out = new ArrayList<>(revoked.size());
        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (Exception e) {
            log.warn("Failed to init X.509 factory: {}", e.getMessage());
            return out;
        }
        for (KeyPair k : revoked) {
            try {
                Date revDate = Date.from(k.getRevokedAt().atZone(ZoneId.systemDefault()).toInstant());
                out.add(new CaService.RevokedEntry(new java.math.BigInteger(k.getCertSerialNumber(), 16), revDate));
            } catch (Exception e) {
                log.warn("Skipping revoked key {} for CRL: {}", k.getId(), e.getMessage());
            }
        }
        return out;
    }

    private String toPem(X509Certificate cert) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(cert.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN CERTIFICATE-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        sb.append("-----END CERTIFICATE-----\n");
        return sb.toString();
    }
}
