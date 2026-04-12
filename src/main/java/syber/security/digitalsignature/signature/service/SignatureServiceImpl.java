package syber.security.digitalsignature.signature.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syber.security.digitalsignature.ca.CaService;
import syber.security.digitalsignature.config.FileStorageProperties;
import syber.security.digitalsignature.crypto.service.CryptoService;
import syber.security.digitalsignature.document.entity.Document;
import syber.security.digitalsignature.document.repository.DocumentRepository;
import syber.security.digitalsignature.exception.BadRequestException;
import syber.security.digitalsignature.exception.FileStorageException;
import syber.security.digitalsignature.exception.ResourceNotFoundException;
import syber.security.digitalsignature.key.entity.KeyPair;
import syber.security.digitalsignature.key.repository.KeyPairRepository;
import syber.security.digitalsignature.signature.dto.SignDocumentRequest;
import syber.security.digitalsignature.signature.entity.Signature;
import syber.security.digitalsignature.signature.repository.SignatureRepository;
import syber.security.digitalsignature.user.entity.User;
import syber.security.digitalsignature.user.repository.UserRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureServiceImpl implements SignatureService {

    private static final String SIGNATURE_ALGORITHM = "SHA512withRSA";
    private static final String STATUS_VALID = "VALID";
    private static final String PDF_MIME = "application/pdf";

    private final SignatureRepository signatureRepository;
    private final DocumentRepository documentRepository;
    private final KeyPairRepository keyPairRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final FileStorageProperties storageProperties;
    private final PdfSigningService pdfSigningService;
    private final CaService caService;

    private Path uploadRoot;

    @PostConstruct
    public void init() {
        uploadRoot = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public Signature signDocument(String email, Long documentId, SignDocumentRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        Document document = documentRepository.findByIdAndUserIdForUpdate(documentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        KeyPair keyPair = keyPairRepository.findByIdAndUserId(request.getKeyId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Key", "id", request.getKeyId()));

        if (keyPair.isRevoked()) {
            throw new BadRequestException("Key has been revoked");
        }

        Path originalPath = resolveUploadPath(document.getStoredPath());

        byte[] content;
        try {
            content = Files.readAllBytes(originalPath);
        } catch (IOException e) {
            throw new FileStorageException("Failed to read document file", e);
        }

        String currentHash = cryptoService.hashSha512(content);
        if (!currentHash.equalsIgnoreCase(document.getSha512Hash())) {
            throw new BadRequestException("Document content has changed since upload — refusing to sign");
        }

        PrivateKey privateKey;
        try {
            privateKey = cryptoService.decryptPrivateKey(keyPair.getEncryptedPrivateKey(), request.getPassword());
        } catch (Exception e) {
            throw new BadRequestException("Incorrect password — failed to decrypt private key");
        }

        String signatureValue = cryptoService.sign(content, privateKey);

        Signature.SignatureBuilder builder = Signature.builder()
                .document(document)
                .user(user)
                .keyPair(keyPair)
                .signatureValue(signatureValue)
                .algorithm(SIGNATURE_ALGORITHM)
                .documentHash(currentHash)
                .verifyStatus(STATUS_VALID);

        if (keyPair.getCertificate() != null) {
            builder.signingCertPem(keyPair.getCertificate());
        }

        if (PDF_MIME.equalsIgnoreCase(document.getMimeType()) && keyPair.getCertificate() != null) {
            try {
                PadesOutcome outcome = signPades(document, keyPair, privateKey, request, user);
                builder.padesPdfPath(outcome.relativePath);
                if (outcome.visible) {
                    builder
                            .signaturePage(outcome.page)
                            .sigRectX(outcome.x)
                            .sigRectY(outcome.y)
                            .sigRectW(outcome.w)
                            .sigRectH(outcome.h);
                }
                document.setCurrentPadesPdfPath(outcome.relativePath);
            } catch (Exception e) {
                log.warn("PAdES signing failed for documentId={}: {}", documentId, e.getMessage(), e);
                throw new BadRequestException("Failed to embed signature in PDF: " + e.getMessage());
            }
        }

        document.setStatus("SIGNED");
        documentRepository.save(document);

        Signature saved = signatureRepository.save(builder.build());
        log.info("Document signed by {}: documentId={}, keyId={}, signatureId={}, pades={}",
                email, documentId, request.getKeyId(), saved.getId(), saved.getPadesPdfPath() != null);
        return saved;
    }

    private PadesOutcome signPades(Document document,
                                    KeyPair keyPair,
                                    PrivateKey privateKey,
                                    SignDocumentRequest request,
                                    User user) throws Exception {
        Path inputPath = document.getCurrentPadesPdfPath() != null
                ? resolveUploadPath(document.getCurrentPadesPdfPath())
                : resolveUploadPath(document.getStoredPath());

        boolean visible = request.getVisible() == null || request.getVisible();

        SignDocumentRequest.SignatureRect rect = request.getSignatureRect();
        int page = request.getSignaturePage() != null ? request.getSignaturePage() : 1;
        double x = rect != null && rect.getX() != null ? rect.getX() : 50.0;
        double y = rect != null && rect.getY() != null ? rect.getY() : 50.0;
        double w = rect != null && rect.getW() != null ? rect.getW() : 200.0;
        double h = rect != null && rect.getH() != null ? rect.getH() : 80.0;

        X509Certificate signerCert = parseCert(keyPair.getCertificate());
        X509Certificate caCert = caService.getCaCertificate();

        String signerName = (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName() : user.getEmail();

        PdfSigningService.SignRequest sr = PdfSigningService.SignRequest.builder()
                .signerPrivateKey(privateKey)
                .signerCert(signerCert)
                .caCert(caCert)
                .signerName(signerName)
                .signerEmail(user.getEmail())
                .reason(request.getReason() != null ? request.getReason() : "Document signed")
                .location(request.getLocation())
                .page(page)
                .rectX(x)
                .rectY(y)
                .rectW(w)
                .rectH(h)
                .visible(visible)
                .build();

        Path outputAbs = pdfSigningService.signPdf(inputPath, sr);
        Path relative = uploadRoot.relativize(outputAbs);
        // For invisible signatures, leave page/rect NULL — there's no widget to preview.
        if (visible) {
            return new PadesOutcome(relative.toString().replace('\\', '/'), page, x, y, w, h, true);
        }
        return new PadesOutcome(relative.toString().replace('\\', '/'), 0, 0, 0, 0, 0, false);
    }

    private X509Certificate parseCert(String pem) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(pem.getBytes()));
    }

    private Path resolveUploadPath(String relative) {
        Path resolved = uploadRoot.resolve(relative).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new FileStorageException("Invalid stored path");
        }
        return resolved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signature> listByDocument(String email, Long documentId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        documentRepository.findByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        return signatureRepository.findByDocumentIdOrderBySignedAtDesc(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signature> listByUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return signatureRepository.findByUserIdOrderBySignedAtDesc(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PadesDownload resolvePadesPdf(String email, Long signatureId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        Signature signature = signatureRepository.findById(signatureId)
                .orElseThrow(() -> new ResourceNotFoundException("Signature", "id", signatureId));
        if (signature.getUser() == null || !signature.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Signature", "id", signatureId);
        }
        if (signature.getPadesPdfPath() == null) {
            throw new BadRequestException("Signature does not have an embedded signed PDF");
        }
        Path file = resolveUploadPath(signature.getPadesPdfPath());
        String suggested = buildDownloadFilename(signature.getDocument().getOriginalName());
        return new PadesDownload(file, suggested);
    }

    // Derives the download filename from the original document name. Using the
    // signature ID would leak the system-wide signature count via enumeration
    // (signed-19.pdf reveals there have been ~19 signatures), so we use the
    // user's own filename + "-signed" suffix instead.
    private String buildDownloadFilename(String originalName) {
        String base = (originalName == null || originalName.isBlank()) ? "document" : originalName;
        int dot = base.lastIndexOf('.');
        if (dot > 0 && dot >= base.length() - 5) {
            base = base.substring(0, dot);
        }
        // Strip path separators and control chars; trim and collapse whitespace.
        String safe = base.replaceAll("[\\\\/\\x00-\\x1F]", "").trim().replaceAll("\\s+", " ");
        if (safe.isEmpty()) safe = "document";
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return safe + "-signed.pdf";
    }

    private record PadesOutcome(String relativePath, int page, double x, double y, double w, double h, boolean visible) {}
}
