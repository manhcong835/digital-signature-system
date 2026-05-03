package syber.security.digitalsignature.signature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import syber.security.digitalsignature.ca.CaService;
import syber.security.digitalsignature.config.FileStorageProperties;
import syber.security.digitalsignature.crypto.service.CryptoService;
import syber.security.digitalsignature.crypto.service.CryptoServiceImpl;
import syber.security.digitalsignature.document.entity.Document;
import syber.security.digitalsignature.document.repository.DocumentRepository;
import syber.security.digitalsignature.exception.ResourceNotFoundException;
import syber.security.digitalsignature.key.entity.KeyPair;
import syber.security.digitalsignature.signature.dto.VerifyResponse;
import syber.security.digitalsignature.signature.entity.Signature;
import syber.security.digitalsignature.signature.repository.SignatureRepository;
import syber.security.digitalsignature.signature.repository.VerificationRepository;
import syber.security.digitalsignature.signature.service.VerificationServiceImpl;
import syber.security.digitalsignature.user.entity.User;
import syber.security.digitalsignature.user.repository.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    private static final String EMAIL = "alice@example.com";
    private static final byte[] CONTENT = "verify-me".getBytes();

    @Mock private SignatureRepository signatureRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private CryptoService cryptoService;
    @Mock private FileStorageProperties storageProperties;
    @Mock private VerificationRepository verificationRepository;
    @Mock private CaService caService;

    @InjectMocks
    private VerificationServiceImpl verificationService;

    @TempDir
    Path uploadDir;

    private User user;
    private Document document;
    private KeyPair keyPair;
    private Signature signature;
    private String computedHash;

    @BeforeEach
    void setUp() throws Exception {
        when(storageProperties.getUploadDir()).thenReturn(uploadDir.toString());
        verificationService.init();

        Path storedFile = uploadDir.resolve("doc-uuid.txt");
        Files.write(storedFile, CONTENT);

        computedHash = "a".repeat(128);

        user = User.builder().id(1L).email(EMAIL).fullName("Alice").build();

        document = Document.builder()
                .id(10L)
                .user(user)
                .originalName("hello.txt")
                .storedPath("doc-uuid.txt")
                .sha512Hash(computedHash)
                .build();

        keyPair = KeyPair.builder()
                .id(20L)
                .name("Signing key")
                .publicKey("pub-base64")
                .algorithm("SHA512withRSA")
                .build();

        signature = Signature.builder()
                .id(30L)
                .document(document)
                .user(user)
                .keyPair(keyPair)
                .signatureValue("sig-base64")
                .algorithm("SHA512withRSA")
                .documentHash(computedHash)
                .verifyStatus("VALID")
                .build();

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(documentRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(document));
    }

    @Test
    void verifyDocument_validSignature_returnsValid() {
        when(cryptoService.hashSha512(CONTENT)).thenReturn(computedHash);
        when(signatureRepository.findByDocumentIdOrderBySignedAtDesc(10L)).thenReturn(List.of(signature));
        PublicKey pk = (PublicKey) java.util.Objects.requireNonNull(
                org.mockito.Mockito.mock(PublicKey.class));
        when(cryptoService.decodePublicKey("pub-base64")).thenReturn(pk);
        when(cryptoService.verify(CONTENT, "sig-base64", pk)).thenReturn(true);

        VerifyResponse response = verificationService.verifyDocument(EMAIL, 10L, null);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getStatus()).isEqualTo("VALID");
        assertThat(response.getSignatures()).hasSize(1);
        assertThat(response.getSignatures().get(0).getStatus()).isEqualTo("VALID");
    }

    @Test
    void verifyDocument_hashMismatch_returnsTampered() {
        when(cryptoService.hashSha512(CONTENT)).thenReturn("different-hash");
        when(signatureRepository.findByDocumentIdOrderBySignedAtDesc(10L)).thenReturn(List.of(signature));

        VerifyResponse response = verificationService.verifyDocument(EMAIL, 10L, null);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getStatus()).isEqualTo("INVALID");
        assertThat(response.getSignatures().get(0).getStatus()).isEqualTo("TAMPERED");
        verify(cryptoService, never()).verify(any(), any(), any());
        verify(signatureRepository).save(any(Signature.class));
    }

    @Test
    void verifyDocument_signatureCryptoFails_returnsInvalid() {
        when(cryptoService.hashSha512(CONTENT)).thenReturn(computedHash);
        when(signatureRepository.findByDocumentIdOrderBySignedAtDesc(10L)).thenReturn(List.of(signature));
        PublicKey pk = org.mockito.Mockito.mock(PublicKey.class);
        when(cryptoService.decodePublicKey("pub-base64")).thenReturn(pk);
        when(cryptoService.verify(CONTENT, "sig-base64", pk)).thenReturn(false);

        VerifyResponse response = verificationService.verifyDocument(EMAIL, 10L, null);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getStatus()).isEqualTo("INVALID");
        assertThat(response.getSignatures().get(0).getStatus()).isEqualTo("INVALID");
        verify(signatureRepository).save(any(Signature.class));
    }

    @Test
    void verifyDocument_noSignatures_returnsNoSignatures() {
        when(cryptoService.hashSha512(CONTENT)).thenReturn(computedHash);
        when(signatureRepository.findByDocumentIdOrderBySignedAtDesc(10L)).thenReturn(List.of());

        VerifyResponse response = verificationService.verifyDocument(EMAIL, 10L, null);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getStatus()).isEqualTo("NO_SIGNATURES");
        assertThat(response.getSignatures()).isNull();
    }

    @Test
    void verifyDocument_unknownDocument_throws404() {
        when(documentRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationService.verifyDocument(EMAIL, 99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyExternal_validRealKeyPair_returnsValid() {
        CryptoService realCrypto = new CryptoServiceImpl();
        VerificationServiceImpl realService = new VerificationServiceImpl(
                signatureRepository, documentRepository, userRepository, realCrypto, storageProperties, verificationRepository, caService);
        realService.init();

        java.security.KeyPair pair = realCrypto.generateKeyPair();
        byte[] data = "external-payload".getBytes();
        String signatureValue = realCrypto.sign(data, pair.getPrivate());
        String publicKeyEncoded = realCrypto.encodePublicKey(pair.getPublic());

        MockMultipartFile file = new MockMultipartFile("file", "ext.txt", "text/plain", data);

        VerifyResponse response = realService.verifyExternal(file, publicKeyEncoded, signatureValue, "SHA512withRSA");

        assertThat(response.isValid()).isTrue();
        assertThat(response.getStatus()).isEqualTo("VALID");
        assertThat(response.getDocumentName()).isEqualTo("ext.txt");
    }

    @Test
    void verifyExternal_malformedPublicKey_returnsInvalidWithoutThrowing() {
        CryptoService realCrypto = new CryptoServiceImpl();
        VerificationServiceImpl realService = new VerificationServiceImpl(
                signatureRepository, documentRepository, userRepository, realCrypto, storageProperties, verificationRepository, caService);
        realService.init();

        MockMultipartFile file = new MockMultipartFile("file", "ext.txt", "text/plain", "data".getBytes());

        VerifyResponse response = realService.verifyExternal(file, "!!!not-a-key!!!", "AAAA", "SHA512withRSA");

        assertThat(response.isValid()).isFalse();
        assertThat(response.getStatus()).isEqualTo("INVALID");
        assertThat(response.getMessage()).contains("Invalid public key");
    }

    @Test
    void verifyExternal_tamperedData_returnsInvalid() {
        CryptoService realCrypto = new CryptoServiceImpl();
        VerificationServiceImpl realService = new VerificationServiceImpl(
                signatureRepository, documentRepository, userRepository, realCrypto, storageProperties, verificationRepository, caService);
        realService.init();

        java.security.KeyPair pair = realCrypto.generateKeyPair();
        byte[] original = "original-data".getBytes();
        byte[] tampered = "tampered-data".getBytes();
        String signatureValue = realCrypto.sign(original, pair.getPrivate());
        String publicKeyEncoded = realCrypto.encodePublicKey(pair.getPublic());

        MockMultipartFile file = new MockMultipartFile("file", "ext.txt", "text/plain", tampered);

        VerifyResponse response = realService.verifyExternal(file, publicKeyEncoded, signatureValue, "SHA512withRSA");

        assertThat(response.isValid()).isFalse();
        assertThat(response.getStatus()).isEqualTo("INVALID");
    }
}
