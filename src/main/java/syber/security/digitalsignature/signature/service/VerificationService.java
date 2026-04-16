package syber.security.digitalsignature.signature.service;

import org.springframework.web.multipart.MultipartFile;
import syber.security.digitalsignature.signature.dto.PadesVerifyResponse;
import syber.security.digitalsignature.signature.dto.VerifyResponse;
import syber.security.digitalsignature.signature.entity.Verification;

import java.util.List;

public interface VerificationService {

    VerifyResponse verifyDocument(String email, Long documentId, Long signatureIdOrNull);

    VerifyResponse verifyExternal(MultipartFile file, String publicKeyPemOrBase64, String signatureBase64, String algorithm);

    List<Verification> getVerificationHistory(String email, Long documentId);

    PadesVerifyResponse verifyPadesPdf(MultipartFile file);
}
