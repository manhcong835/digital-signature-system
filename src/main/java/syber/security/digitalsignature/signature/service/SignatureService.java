package syber.security.digitalsignature.signature.service;

import syber.security.digitalsignature.signature.dto.SignDocumentRequest;
import syber.security.digitalsignature.signature.entity.Signature;

import java.nio.file.Path;
import java.util.List;

public interface SignatureService {

    Signature signDocument(String email, Long documentId, SignDocumentRequest request);

    List<Signature> listByDocument(String email, Long documentId);

    List<Signature> listByUser(String email);

    PadesDownload resolvePadesPdf(String email, Long signatureId);

    record PadesDownload(Path file, String suggestedFilename) {}
}
