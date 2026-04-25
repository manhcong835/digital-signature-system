package syber.security.digitalsignature.signature.service;

import lombok.Builder;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public interface PdfSigningService {

    Path signPdf(Path inputPdf, SignRequest req) throws IOException;

    @Data
    @Builder
    class SignRequest {
        private PrivateKey signerPrivateKey;
        private X509Certificate signerCert;
        private X509Certificate caCert;
        private String signerName;
        private String signerEmail;
        private String reason;
        private String location;
        private int page;          // 1-based
        private double rectX;      // in PDF points (origin bottom-left)
        private double rectY;
        private double rectW;
        private double rectH;
        private boolean visible;   // false → embedded invisible signature, rect/page ignored
    }
}
