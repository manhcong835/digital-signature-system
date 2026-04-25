package syber.security.digitalsignature.signature.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import syber.security.digitalsignature.ca.CaService;
import syber.security.digitalsignature.crypto.service.CryptoService;
import syber.security.digitalsignature.key.entity.KeyPair;
import syber.security.digitalsignature.key.repository.KeyPairRepository;
import syber.security.digitalsignature.key.service.KeyServiceImpl;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;

@Slf4j
@Component
public class LegacyKeyBackfillRunner implements ApplicationRunner {

    private final KeyPairRepository keyPairRepository;
    private final CaService caService;
    private final CryptoService cryptoService;
    private final TransactionTemplate txTemplate;

    @Value("${signature.backfill.enabled:true}")
    private boolean enabled;

    public LegacyKeyBackfillRunner(KeyPairRepository keyPairRepository,
                                   CaService caService,
                                   CryptoService cryptoService,
                                   PlatformTransactionManager txManager) {
        this.keyPairRepository = keyPairRepository;
        this.caService = caService;
        this.cryptoService = cryptoService;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Legacy key backfill disabled (signature.backfill.enabled=false)");
            return;
        }

        List<KeyPair> candidates = keyPairRepository.findAll().stream()
                .filter(k -> k.getCertificate() == null && !k.isRevoked())
                .toList();

        if (candidates.isEmpty()) {
            log.debug("No legacy keys to backfill with certificates");
            return;
        }

        log.info("Backfilling X.509 certificates for {} legacy key(s)", candidates.size());

        int success = 0;
        int failure = 0;
        for (KeyPair key : candidates) {
            try {
                txTemplate.executeWithoutResult(status -> {
                    try {
                        KeyPair fresh = keyPairRepository.findById(key.getId()).orElseThrow();
                        if (fresh.getCertificate() != null || fresh.isRevoked()) {
                            return;
                        }
                        PublicKey publicKey = cryptoService.decodePublicKey(fresh.getPublicKey());
                        String cn = (fresh.getUser().getFullName() != null && !fresh.getUser().getFullName().isBlank())
                                ? fresh.getUser().getFullName()
                                : fresh.getUser().getEmail();
                        X509Certificate cert = caService.issueCertificate(publicKey, cn, fresh.getUser().getEmail());
                        KeyServiceImpl.applyCertificate(fresh, cert);
                        keyPairRepository.save(fresh);
                    } catch (Exception inner) {
                        throw new RuntimeException(inner);
                    }
                });
                success++;
            } catch (Exception e) {
                failure++;
                log.warn("Backfill failed for keyId={}: {}", key.getId(), e.getMessage());
            }
        }
        log.info("Legacy key backfill complete: success={}, failure={}", success, failure);
    }
}
