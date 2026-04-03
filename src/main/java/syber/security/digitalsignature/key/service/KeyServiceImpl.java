package syber.security.digitalsignature.key.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syber.security.digitalsignature.ca.CaService;
import syber.security.digitalsignature.crypto.service.CryptoService;
import syber.security.digitalsignature.exception.BadRequestException;
import syber.security.digitalsignature.exception.ResourceNotFoundException;
import syber.security.digitalsignature.key.dto.CreateKeyRequest;
import syber.security.digitalsignature.key.entity.KeyPair;
import syber.security.digitalsignature.key.repository.KeyPairRepository;
import syber.security.digitalsignature.signature.repository.SignatureRepository;
import syber.security.digitalsignature.user.entity.User;
import syber.security.digitalsignature.user.repository.UserRepository;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyServiceImpl implements KeyService {

    private static final String DEFAULT_ALGORITHM = "SHA512withRSA";

    private final KeyPairRepository keyPairRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final PasswordEncoder passwordEncoder;
    private final SignatureRepository signatureRepository;
    private final CaService caService;

    @Override
    @Transactional
    public KeyPair createKey(String email, CreateKeyRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Password is incorrect");
        }

        java.security.KeyPair generated = cryptoService.generateKeyPair();
        PublicKey publicKey = generated.getPublic();
        PrivateKey privateKey = generated.getPrivate();

        String publicKeyBase64 = cryptoService.encodePublicKey(publicKey);
        String encryptedPrivateKey = cryptoService.encryptPrivateKey(privateKey, request.getPassword());

        boolean isFirstKey = keyPairRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).isEmpty();

        KeyPair keyPair = KeyPair.builder()
                .user(user)
                .name(request.getName())
                .publicKey(publicKeyBase64)
                .encryptedPrivateKey(encryptedPrivateKey)
                .algorithm(DEFAULT_ALGORITHM)
                .isDefault(isFirstKey)
                .build();

        try {
            X509Certificate cert = caService.issueCertificate(publicKey, displayNameFor(user), user.getEmail());
            applyCertificate(keyPair, cert);
        } catch (Exception e) {
            log.warn("Failed to issue certificate for new key (user={}): {}", email, e.getMessage());
        }

        KeyPair saved = keyPairRepository.save(keyPair);
        log.info("Key created for user {}: keyId={}, default={}", email, saved.getId(), isFirstKey);
        return saved;
    }

    public static void applyCertificate(KeyPair keyPair, X509Certificate cert) throws Exception {
        keyPair.setCertificate(toPem(cert));
        keyPair.setCertSerialNumber(cert.getSerialNumber().toString(16));
        keyPair.setCertIssuedAt(LocalDateTime.ofInstant(cert.getNotBefore().toInstant(), ZoneId.systemDefault()));
        keyPair.setCertExpiresAt(LocalDateTime.ofInstant(cert.getNotAfter().toInstant(), ZoneId.systemDefault()));
    }

    private static String toPem(X509Certificate cert) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(cert.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN CERTIFICATE-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        sb.append("-----END CERTIFICATE-----\n");
        return sb.toString();
    }

    private String displayNameFor(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getEmail();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KeyPair> listKeys(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return keyPairRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public KeyPair getKey(String email, Long keyId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return keyPairRepository.findByIdAndUserId(keyId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Key", "id", keyId));
    }

    @Override
    @Transactional
    public KeyPair setDefault(String email, Long keyId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        KeyPair target = keyPairRepository.findByIdAndUserId(keyId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Key", "id", keyId));

        Optional<KeyPair> currentDefault = keyPairRepository.findByUserIdAndIsDefaultTrue(user.getId());
        currentDefault.ifPresent(k -> {
            if (!k.getId().equals(keyId)) {
                k.setIsDefault(false);
                keyPairRepository.save(k);
            }
        });

        target.setIsDefault(true);
        KeyPair saved = keyPairRepository.save(target);
        log.info("Default key set for user {}: keyId={}", email, keyId);
        return saved;
    }

    @Override
    @Transactional
    public KeyPair revokeKey(String email, Long keyId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        KeyPair key = keyPairRepository.findByIdAndUserId(keyId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Key", "id", keyId));

        if (key.isRevoked()) {
            throw new BadRequestException("Key is already revoked");
        }

        key.setRevokedAt(java.time.LocalDateTime.now());
        if (Boolean.TRUE.equals(key.getIsDefault())) {
            key.setIsDefault(false);
        }
        KeyPair saved = keyPairRepository.save(key);
        log.info("Key revoked for user {}: keyId={}", email, keyId);
        return saved;
    }

    @Override
    @Transactional
    public void deleteKey(String email, Long keyId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        KeyPair key = keyPairRepository.findByIdAndUserId(keyId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Key", "id", keyId));

        boolean hasSignatures = signatureRepository.existsByKeyPairId(keyId);

        if (!key.isRevoked() && hasSignatures) {
            throw new BadRequestException("Cannot delete active key that has already signed documents. Revoke the key first.");
        }

        keyPairRepository.delete(key);
        log.info("Key deleted for user {}: keyId={}", email, keyId);
    }
}
