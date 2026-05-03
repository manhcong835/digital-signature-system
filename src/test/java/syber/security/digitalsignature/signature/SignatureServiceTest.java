package syber.security.digitalsignature.signature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import syber.security.digitalsignature.config.FileStorageProperties;
import syber.security.digitalsignature.crypto.service.CryptoService;
import syber.security.digitalsignature.document.entity.Document;
import syber.security.digitalsignature.document.repository.DocumentRepository;
import syber.security.digitalsignature.exception.BadRequestException;
import syber.security.digitalsignature.exception.ResourceNotFoundException;
import syber.security.digitalsignature.key.entity.KeyPair;
import syber.security.digitalsignature.key.repository.KeyPairRepository;
import syber.security.digitalsignature.signature.dto.SignDocumentRequest;
import syber.security.digitalsignature.signature.entity.Signature;
import syber.security.digitalsignature.signature.repository.SignatureRepository;
import syber.security.digitalsignature.signature.service.SignatureServiceImpl;
import syber.security.digitalsignature.user.entity.User;
import syber.security.digitalsignature.user.repository.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureServiceTest {

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "Str0ngP@ssw0rd";
    private static final byte[] CONTENT = "hello signature".getBytes();
    private static final String SHA512_OF_CONTENT =
            "0123456789abcdef".repeat(8); // 128 chars; will be mocked from cryptoService

    @Mock private SignatureRepository signatureRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private KeyPairRepository keyPairRepository;
    @Mock private UserRepository userRepository;
    @Mock private CryptoService cryptoService;
    @Mock private FileStorageProperties storageProperties;
    @Mock private PrivateKey privateKey;

    @InjectMocks
    private SignatureServiceImpl signatureService;

    @TempDir
    Path uploadDir;

    private User user;
    private Document document;
    private KeyPair keyPair;
    private Path storedFile;

    @BeforeEach
    void setUp() throws Exception {
        when(storageProperties.getUploadDir()).thenReturn(uploadDir.toString());
        signatureService.init();

        storedFile = uploadDir.resolve("doc-uuid.txt");
        Files.write(storedFile, CONTENT);

        user = User.builder().id(1L).email(EMAIL).fullName("Alice").build();

        document = Document.builder()
                .id(10L)
                .user(user)
                .originalName("hello.txt")
                .storedPath("doc-uuid.txt")
                .sha512Hash(SHA512_OF_CONTENT)
                .build();

        keyPair = KeyPair.builder()
                .id(20L)
                .name("Signing key")
                .encryptedPrivateKey("encrypted-blob")
                .publicKey("pub")
                .algorithm("SHA512withRSA")
                .build();

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(documentRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(document));
        lenient().when(keyPairRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.of(keyPair));
    }

    @Test
    void signDocument_happyPath_persistsSignature() {
        when(cryptoService.hashSha512(CONTENT)).thenReturn(SHA512_OF_CONTENT);
        when(cryptoService.decryptPrivateKey("encrypted-blob", PASSWORD)).thenReturn(privateKey);
        when(cryptoService.sign(CONTENT, privateKey)).thenReturn("signature-base64");
        when(signatureRepository.save(any(Signature.class))).thenAnswer(i -> i.getArgument(0));

        SignDocumentRequest req = new SignDocumentRequest();
        req.setKeyId(20L);
        req.setPassword(PASSWORD);

        Signature result = signatureService.signDocument(EMAIL, 10L, req);

        assertThat(result.getSignatureValue()).isEqualTo("signature-base64");
        assertThat(result.getAlgorithm()).isEqualTo("SHA512withRSA");
        assertThat(result.getDocumentHash()).isEqualTo(SHA512_OF_CONTENT);
        assertThat(result.getVerifyStatus()).isEqualTo("VALID");
        verify(signatureRepository).save(any(Signature.class));
    }

    @Test
    void signDocument_wrongPassword_throwsBadRequest() {
        when(cryptoService.hashSha512(CONTENT)).thenReturn(SHA512_OF_CONTENT);
        when(cryptoService.decryptPrivateKey("encrypted-blob", "wrong"))
                .thenThrow(new RuntimeException("decrypt failed"));

        SignDocumentRequest req = new SignDocumentRequest();
        req.setKeyId(20L);
        req.setPassword("wrong");

        assertThatThrownBy(() -> signatureService.signDocument(EMAIL, 10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Incorrect password");
        verify(signatureRepository, never()).save(any());
    }

    @Test
    void signDocument_tamperedFile_throwsBadRequest() {
        when(cryptoService.hashSha512(CONTENT)).thenReturn("different-hash-than-stored");

        SignDocumentRequest req = new SignDocumentRequest();
        req.setKeyId(20L);
        req.setPassword(PASSWORD);

        assertThatThrownBy(() -> signatureService.signDocument(EMAIL, 10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("changed since upload");
        verify(signatureRepository, never()).save(any());
    }

    @Test
    void signDocument_unknownDocument_throws404() {
        when(documentRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        SignDocumentRequest req = new SignDocumentRequest();
        req.setKeyId(20L);
        req.setPassword(PASSWORD);

        assertThatThrownBy(() -> signatureService.signDocument(EMAIL, 99L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void signDocument_unknownKey_throws404() {
        when(keyPairRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        SignDocumentRequest req = new SignDocumentRequest();
        req.setKeyId(99L);
        req.setPassword(PASSWORD);

        assertThatThrownBy(() -> signatureService.signDocument(EMAIL, 10L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
