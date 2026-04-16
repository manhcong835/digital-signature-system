package syber.security.digitalsignature.signature.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import syber.security.digitalsignature.ca.CaService;
import syber.security.digitalsignature.config.FileStorageProperties;
import syber.security.digitalsignature.crypto.service.CryptoService;
import syber.security.digitalsignature.document.entity.Document;
import syber.security.digitalsignature.document.repository.DocumentRepository;
import syber.security.digitalsignature.exception.BadRequestException;
import syber.security.digitalsignature.exception.FileStorageException;
import syber.security.digitalsignature.exception.ResourceNotFoundException;
import syber.security.digitalsignature.signature.dto.PadesVerifyResponse;
import syber.security.digitalsignature.signature.dto.VerifyResponse;
import syber.security.digitalsignature.signature.entity.Signature;
import syber.security.digitalsignature.signature.entity.Verification;
import syber.security.digitalsignature.signature.repository.SignatureRepository;
import syber.security.digitalsignature.signature.repository.VerificationRepository;
import syber.security.digitalsignature.user.entity.User;
import syber.security.digitalsignature.user.repository.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private static final String DEFAULT_ALGORITHM = "SHA512withRSA";
    private static final String STATUS_VALID = "VALID";
    private static final String STATUS_INVALID = "INVALID";
    private static final String STATUS_TAMPERED = "TAMPERED";

    private final SignatureRepository signatureRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final FileStorageProperties storageProperties;
    private final VerificationRepository verificationRepository;
    private final CaService caService;

    private Path uploadRoot;

    @PostConstruct
    public void init() {
        uploadRoot = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public VerifyResponse verifyDocument(String email, Long documentId, Long signatureIdOrNull) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        Document document = documentRepository.findByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        Path filePath = uploadRoot.resolve(document.getStoredPath()).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            throw new FileStorageException("Invalid stored path");
        }

        byte[] content;
        try {
            content = Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new FileStorageException("Failed to read document file", e);
        }

        String currentHash = cryptoService.hashSha512(content);

        if (signatureIdOrNull != null) {
            Signature signature = signatureRepository.findById(signatureIdOrNull)
                    .orElseThrow(() -> new ResourceNotFoundException("Signature", "id", signatureIdOrNull));
            if (!signature.getDocument().getId().equals(documentId)) {
                throw new BadRequestException("Signature does not belong to the requested document");
            }
            VerifyResponse single = verifySingle(signature, content, currentHash, document.getOriginalName());
            updateStoredStatus(signature, single, user);

            // Check all signatures status
            List<Signature> currentSignatures = signatureRepository.findByDocumentIdOrderBySignedAtDesc(documentId);
            boolean isAllValid = !currentSignatures.isEmpty() && currentSignatures.stream()
                    .allMatch(s -> STATUS_VALID.equals(s.getVerifyStatus()));
            if (isAllValid) {
                document.setStatus("VERIFIED");
                documentRepository.save(document);
            }

            return single;
        }

        List<Signature> all = signatureRepository.findByDocumentIdOrderBySignedAtDesc(documentId);

        if (all.isEmpty()) {
            return VerifyResponse.builder()
                    .valid(false)
                    .status("NO_SIGNATURES")
                    .message("No signatures found for this document")
                    .documentName(document.getOriginalName())
                    .computedHash(currentHash)
                    .verifiedAt(LocalDateTime.now())
                    .build();
        }

        List<VerifyResponse> items = new ArrayList<>();
        boolean allValid = true;
        for (Signature s : all) {
            VerifyResponse item = verifySingle(s, content, currentHash, document.getOriginalName());
            updateStoredStatus(s, item, user);
            items.add(item);
            if (!item.isValid()) {
                allValid = false;
            }
        }

        if (allValid) {
            document.setStatus("VERIFIED");
            documentRepository.save(document);
        }

        return VerifyResponse.builder()
                .valid(allValid)
                .status(allValid ? STATUS_VALID : STATUS_INVALID)
                .message(allValid
                        ? "All signatures verified successfully"
                        : "One or more signatures failed verification")
                .documentName(document.getOriginalName())
                .computedHash(currentHash)
                .verifiedAt(LocalDateTime.now())
                .signatures(items)
                .build();
    }

    private VerifyResponse verifySingle(Signature signature, byte[] content, String currentHash, String documentName) {
        String expectedHash = signature.getDocumentHash();
        boolean hashMatches = expectedHash != null && expectedHash.equalsIgnoreCase(currentHash);

        VerifyResponse.VerifyResponseBuilder builder = VerifyResponse.builder()
                .documentName(documentName)
                .algorithm(signature.getAlgorithm())
                .expectedHash(expectedHash)
                .computedHash(currentHash)
                .hashMatches(hashMatches)
                .signerEmail(signature.getUser() != null ? signature.getUser().getEmail() : null)
                .signerName(signature.getUser() != null ? signature.getUser().getFullName() : null)
                .keyName(signature.getKeyPair() != null ? signature.getKeyPair().getName() : null)
                .signedAt(signature.getSignedAt())
                .verifiedAt(LocalDateTime.now());

        if (!hashMatches) {
            return builder
                    .valid(false)
                    .signatureValid(false)
                    .status(STATUS_TAMPERED)
                    .message("Document content has changed since signing — hash does not match")
                    .build();
        }

        if (signature.getKeyPair() == null || signature.getKeyPair().getPublicKey() == null) {
            return builder
                    .valid(false)
                    .signatureValid(false)
                    .status(STATUS_INVALID)
                    .message("Signing key is no longer available")
                    .build();
        }

        boolean signatureValid;
        try {
            PublicKey publicKey = cryptoService.decodePublicKey(signature.getKeyPair().getPublicKey());
            signatureValid = cryptoService.verify(content, signature.getSignatureValue(), publicKey);
        } catch (Exception e) {
            log.warn("Verification error for signatureId={}: {}", signature.getId(), e.getMessage());
            return builder
                    .valid(false)
                    .signatureValid(false)
                    .status(STATUS_INVALID)
                    .message("Verification failed: " + e.getMessage())
                    .build();
        }

        boolean isKeyRevoked = signature.getKeyPair() != null && signature.getKeyPair().isRevoked();

        return builder
                .valid(signatureValid)
                .signatureValid(signatureValid)
                .status(signatureValid ? STATUS_VALID : STATUS_INVALID)
                .message(signatureValid
                        ? (isKeyRevoked ? "Signature is valid (Warning: Key has been revoked)" : "Signature is valid")
                        : "Signature is invalid — cryptographic verification failed")
                .build();
    }

    private void updateStoredStatus(Signature signature, VerifyResponse result, User verifier) {
        String newStatus = result.getStatus();
        if (newStatus != null && !newStatus.equals(signature.getVerifyStatus())) {
            signature.setVerifyStatus(newStatus);
            signatureRepository.save(signature);
        }

        Verification verification = Verification.builder()
                .signature(signature)
                .user(verifier)
                .isValid(result.isValid())
                .status(newStatus != null ? newStatus : STATUS_INVALID)
                .message(result.getMessage())
                .computedHash(result.getComputedHash())
                .build();
        verificationRepository.save(verification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Verification> getVerificationHistory(String email, Long documentId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        documentRepository.findByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        return verificationRepository.findByDocumentIdOrderByVerifiedAtDesc(documentId);
    }

    @Override
    public VerifyResponse verifyExternal(MultipartFile file, String publicKeyInput, String signatureBase64, String algorithm) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        if (publicKeyInput == null || publicKeyInput.isBlank()) {
            throw new BadRequestException("Public key is required");
        }
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            throw new BadRequestException("Signature is required");
        }

        String algo = (algorithm == null || algorithm.isBlank()) ? DEFAULT_ALGORITHM : algorithm.trim();
        String fileName = file.getOriginalFilename();

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new FileStorageException("Failed to read uploaded file", e);
        }

        String computedHash = cryptoService.hashSha512(content);
        String cleanedKey = cleanPublicKey(publicKeyInput);
        String cleanedSig = signatureBase64.replaceAll("\\s+", "");

        PublicKey publicKey;
        try {
            publicKey = cryptoService.decodePublicKey(cleanedKey);
        } catch (Exception e) {
            return VerifyResponse.builder()
                    .valid(false)
                    .signatureValid(false)
                    .status(STATUS_INVALID)
                    .message("Invalid public key: " + e.getMessage())
                    .documentName(fileName)
                    .algorithm(algo)
                    .computedHash(computedHash)
                    .verifiedAt(LocalDateTime.now())
                    .build();
        }

        boolean signatureValid;
        try {
            if (DEFAULT_ALGORITHM.equalsIgnoreCase(algo)) {
                signatureValid = cryptoService.verify(content, cleanedSig, publicKey);
            } else {
                signatureValid = verifyWithAlgorithm(content, cleanedSig, publicKey, algo);
            }
        } catch (Exception e) {
            return VerifyResponse.builder()
                    .valid(false)
                    .signatureValid(false)
                    .status(STATUS_INVALID)
                    .message("Verification failed: " + e.getMessage())
                    .documentName(fileName)
                    .algorithm(algo)
                    .computedHash(computedHash)
                    .verifiedAt(LocalDateTime.now())
                    .build();
        }

        return VerifyResponse.builder()
                .valid(signatureValid)
                .signatureValid(signatureValid)
                .status(signatureValid ? STATUS_VALID : STATUS_INVALID)
                .message(signatureValid
                        ? "Signature is valid"
                        : "Signature is invalid — verification failed")
                .documentName(fileName)
                .algorithm(algo)
                .computedHash(computedHash)
                .verifiedAt(LocalDateTime.now())
                .build();
    }

    private boolean verifyWithAlgorithm(byte[] data, String signatureBase64, PublicKey publicKey, String algorithm) throws Exception {
        java.security.Signature sig = java.security.Signature.getInstance(algorithm);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(java.util.Base64.getDecoder().decode(signatureBase64));
    }

    private String cleanPublicKey(String input) {
        return input
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    @Override
    public PadesVerifyResponse verifyPadesPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("PDF file is required");
        }

        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            throw new FileStorageException("Failed to read uploaded PDF", e);
        }

        List<PadesVerifyResponse.Item> items = new ArrayList<>();
        List<int[]> itemByteRanges = new ArrayList<>();

        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            List<PDSignature> sigs = doc.getSignatureDictionaries();
            if (sigs.isEmpty()) {
                return PadesVerifyResponse.builder()
                        .signatureCount(0)
                        .allSignaturesValid(false)
                        .signatures(items)
                        .build();
            }

            X509Certificate trustedCa = caService.getCaCertificate();

            for (PDSignature sig : sigs) {
                PadesVerifyResponse.Item.ItemBuilder b = PadesVerifyResponse.Item.builder();
                int[] capturedByteRange = null;
                try {
                    byte[] signedContent = sig.getSignedContent(pdfBytes);
                    byte[] cms = sig.getContents(pdfBytes);

                    int[] byteRange = sig.getByteRange();
                    capturedByteRange = byteRange;
                    boolean byteRangeOk = checkByteRangeCoversFile(byteRange, pdfBytes.length, sig.getCOSObject());
                    b.byteRangeCoversFile(byteRangeOk);

                    if (sig.getSignDate() != null) {
                        b.signedAt(LocalDateTime.ofInstant(sig.getSignDate().toInstant(), ZoneId.systemDefault()));
                    }

                    CMSSignedData signedData = new CMSSignedData(new CMSProcessableByteArray(signedContent), cms);

                    Collection<X509CertificateHolder> certHolders = signedData.getCertificates().getMatches(null);
                    X509Certificate signerCert = null;
                    SignerInformation signerInfo = signedData.getSignerInfos().getSigners().iterator().next();
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    List<X509Certificate> certChain = new ArrayList<>();
                    for (X509CertificateHolder h : certHolders) {
                        X509Certificate c = (X509Certificate) cf.generateCertificate(
                                new java.io.ByteArrayInputStream(h.getEncoded()));
                        certChain.add(c);
                        if (h.getSerialNumber().equals(signerInfo.getSID().getSerialNumber())) {
                            signerCert = c;
                        }
                    }
                    if (signerCert == null && !certChain.isEmpty()) signerCert = certChain.get(0);

                    if (signerCert != null) {
                        b.signerCn(extractCn(signerCert.getSubjectX500Principal().getName()));
                        b.issuerCn(extractCn(signerCert.getIssuerX500Principal().getName()));
                        b.certNotBefore(LocalDateTime.ofInstant(signerCert.getNotBefore().toInstant(), ZoneId.systemDefault()));
                        b.certNotAfter(LocalDateTime.ofInstant(signerCert.getNotAfter().toInstant(), ZoneId.systemDefault()));
                    }

                    boolean cryptoValid = false;
                    if (signerCert != null) {
                        try {
                            cryptoValid = signerInfo.verify(
                                    new JcaSimpleSignerInfoVerifierBuilder()
                                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                            .build(signerCert));
                        } catch (Exception ex) {
                            log.debug("CMS verify failed: {}", ex.getMessage());
                        }
                    }
                    b.cryptoValid(cryptoValid);

                    boolean trusted = false;
                    boolean chainValid = false;
                    if (signerCert != null) {
                        try {
                            Set<TrustAnchor> anchors = new HashSet<>();
                            anchors.add(new TrustAnchor(trustedCa, null));
                            java.security.cert.CertPath certPath = cf.generateCertPath(certChain);
                            java.security.cert.PKIXParameters params = new java.security.cert.PKIXParameters(anchors);
                            params.setRevocationEnabled(false);
                            java.security.cert.CertPathValidator validator =
                                    java.security.cert.CertPathValidator.getInstance("PKIX");
                            validator.validate(certPath, params);
                            chainValid = true;
                            trusted = true;
                        } catch (Exception ex) {
                            try {
                                signerCert.verify(trustedCa.getPublicKey());
                                chainValid = true;
                                trusted = true;
                            } catch (Exception inner) {
                                log.debug("Chain validation failed: {}", inner.getMessage());
                            }
                        }
                    }
                    b.certChainValid(chainValid);
                    b.certTrustedByLocalCa(trusted);
                } catch (Exception ex) {
                    b.cryptoValid(false);
                    b.byteRangeCoversFile(false);
                    b.error(ex.getMessage());
                }
                items.add(b.build());
                itemByteRanges.add(capturedByteRange);
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to parse PDF: " + e.getMessage());
        }

        computeIncrementalUpdateValid(items, itemByteRanges, pdfBytes.length);

        boolean allValid = !items.isEmpty();
        for (PadesVerifyResponse.Item it : items) {
            boolean ok = it.isCryptoValid() && it.isCertChainValid()
                    && (it.isByteRangeCoversFile() || it.isIncrementalUpdateValid());
            if (!ok) {
                allValid = false;
                break;
            }
        }

        return PadesVerifyResponse.builder()
                .signatureCount(items.size())
                .allSignaturesValid(allValid)
                .signatures(items)
                .build();
    }

    // For a signature whose ByteRange does not extend to EOF, mark it as still valid
    // if a LATER signature (ByteRange starts at 0, extends past this one's end, cryptoValid)
    // covers the appended bytes — the PAdES "allowed change: additional signature" rule.
    private void computeIncrementalUpdateValid(List<PadesVerifyResponse.Item> items,
                                               List<int[]> byteRanges,
                                               int fileLength) {
        for (int i = 0; i < items.size(); i++) {
            PadesVerifyResponse.Item item = items.get(i);
            if (item.isByteRangeCoversFile()) {
                item.setIncrementalUpdateValid(true);
                continue;
            }
            int[] br = byteRanges.get(i);
            if (br == null || br.length != 4) {
                item.setIncrementalUpdateValid(false);
                continue;
            }
            int endI = br[2] + br[3];
            boolean covered = false;
            for (int j = 0; j < items.size(); j++) {
                if (i == j) continue;
                int[] brJ = byteRanges.get(j);
                if (brJ == null || brJ.length != 4) continue;
                if (brJ[0] != 0) continue;
                int endJ = brJ[2] + brJ[3];
                if (endJ > endI && items.get(j).isCryptoValid()) {
                    covered = true;
                    break;
                }
            }
            item.setIncrementalUpdateValid(covered);
        }
    }

    private boolean checkByteRangeCoversFile(int[] byteRange, int fileLength, COSDictionary sigDict) {
        if (byteRange == null || byteRange.length != 4) return false;
        int end = byteRange[2] + byteRange[3];
        return byteRange[0] == 0 && end == fileLength;
    }

    private String extractCn(String dn) {
        if (dn == null) return null;
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return dn;
    }
}
