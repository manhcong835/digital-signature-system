package syber.security.digitalsignature.ca;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class CaServiceImpl implements CaService {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String CA_ALIAS = "root-ca";

    private final CaProperties props;
    private final SecureRandom random = new SecureRandom();

    private X509Certificate caCertificate;
    private PrivateKey caPrivateKey;

    public CaServiceImpl(CaProperties props, BouncyCastleProvider bcProvider) {
        this.props = props;
        try {
            ensureCaExists();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize CA: " + e.getMessage(), e);
        }
    }

    private void ensureCaExists() throws Exception {
        Path keystorePath = Paths.get(props.getKeystorePath()).toAbsolutePath();
        Files.createDirectories(keystorePath.getParent());

        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        char[] password = props.getKeystorePassword().toCharArray();

        if (Files.exists(keystorePath)) {
            try (FileInputStream fis = new FileInputStream(keystorePath.toFile())) {
                ks.load(fis, password);
            }
            this.caPrivateKey = (PrivateKey) ks.getKey(CA_ALIAS, password);
            this.caCertificate = (X509Certificate) ks.getCertificate(CA_ALIAS);
            log.info("CA loaded from {} (subject={}, expires={})",
                    keystorePath, caCertificate.getSubjectX500Principal(), caCertificate.getNotAfter());
            return;
        }

        log.info("CA keystore not found; generating new self-signed root CA at {}", keystorePath);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(4096, random);
        KeyPair caKeyPair = kpg.generateKeyPair();

        X500Name issuerSubject = new X500Name("CN=" + props.getRootCn());
        BigInteger serial = new BigInteger(64, random).abs();
        Date notBefore = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(notBefore);
        cal.add(Calendar.YEAR, props.getRootValidityYears());
        Date notAfter = cal.getTime();

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerSubject, serial, notBefore, notAfter, issuerSubject, caKeyPair.getPublic());

        BcX509ExtensionUtils extUtils = new BcX509ExtensionUtils();
        SubjectKeyIdentifier ski = extUtils.createSubjectKeyIdentifier(
                org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(caKeyPair.getPublic().getEncoded()));

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false, ski);

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(caKeyPair.getPrivate());

        X509Certificate caCert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));

        ks.load(null, password);
        ks.setKeyEntry(CA_ALIAS, caKeyPair.getPrivate(), password, new X509Certificate[]{caCert});
        try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
            ks.store(fos, password);
        }

        this.caPrivateKey = caKeyPair.getPrivate();
        this.caCertificate = caCert;
        log.info("CA generated: subject={}, serial={}, expires={}",
                caCert.getSubjectX500Principal(), caCert.getSerialNumber(), caCert.getNotAfter());
    }

    @Override
    public X509Certificate issueCertificate(PublicKey subjectPublicKey, String cn, String email) {
        try {
            String safeCn = (cn == null || cn.isBlank()) ? "Digital Signature User" : cn;
            X500Name subject = new X500Name("CN=" + escape(safeCn));
            X500Name issuer = new X500Name(caCertificate.getSubjectX500Principal().getName());

            BigInteger serial = new BigInteger(64, random).abs();
            Date notBefore = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(notBefore);
            cal.add(Calendar.YEAR, props.getCertValidityYears());
            Date notAfter = cal.getTime();

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, subject, subjectPublicKey);

            BcX509ExtensionUtils extUtils = new BcX509ExtensionUtils();
            SubjectKeyIdentifier ski = extUtils.createSubjectKeyIdentifier(
                    org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(subjectPublicKey.getEncoded()));
            AuthorityKeyIdentifier aki = extUtils.createAuthorityKeyIdentifier(
                    org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(caCertificate.getPublicKey().getEncoded()));

            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_emailProtection));
            builder.addExtension(Extension.subjectKeyIdentifier, false, ski);
            builder.addExtension(Extension.authorityKeyIdentifier, false, aki);

            if (email != null && !email.isBlank()) {
                GeneralNames san = new GeneralNames(new GeneralName(GeneralName.rfc822Name, email));
                builder.addExtension(Extension.subjectAlternativeName, false, san);
            }

            if (props.getCrlDistributionUrl() != null && !props.getCrlDistributionUrl().isBlank()) {
                GeneralName gn = new GeneralName(GeneralName.uniformResourceIdentifier, props.getCrlDistributionUrl());
                DistributionPointName dpn = new DistributionPointName(new GeneralNames(gn));
                DistributionPoint dp = new DistributionPoint(dpn, null, null);
                CRLDistPoint crlDistPoint = new CRLDistPoint(new DistributionPoint[]{dp});
                builder.addExtension(Extension.cRLDistributionPoints, false, crlDistPoint);
            }

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(caPrivateKey);

            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue certificate for CN=" + cn + ": " + e.getMessage(), e);
        }
    }

    @Override
    public X509Certificate getCaCertificate() {
        return caCertificate;
    }

    @Override
    public PrivateKey getCaPrivateKey() {
        return caPrivateKey;
    }

    @Override
    public X509CRL buildCrl(List<RevokedEntry> revokedEntries) {
        try {
            Date now = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            cal.add(Calendar.DAY_OF_MONTH, 7);
            Date nextUpdate = cal.getTime();

            X500Name issuer = new X500Name(caCertificate.getSubjectX500Principal().getName());
            X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, now);
            crlBuilder.setNextUpdate(nextUpdate);

            if (revokedEntries != null) {
                for (RevokedEntry e : revokedEntries) {
                    crlBuilder.addCRLEntry(e.serial(), e.revocationDate(), CRLReason.privilegeWithdrawn);
                }
            }

            BcX509ExtensionUtils extUtils = new BcX509ExtensionUtils();
            AuthorityKeyIdentifier aki = extUtils.createAuthorityKeyIdentifier(
                    org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(caCertificate.getPublicKey().getEncoded()));
            crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, aki);
            crlBuilder.addExtension(Extension.cRLNumber, false,
                    new CRLNumber(BigInteger.valueOf(System.currentTimeMillis())));

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(caPrivateKey);
            X509CRLHolder crlHolder = crlBuilder.build(signer);

            return new JcaX509CRLConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCRL(crlHolder);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build CRL: " + e.getMessage(), e);
        }
    }

    private String escape(String value) {
        return value.replace(",", "\\,").replace("=", "\\=");
    }

    @PreDestroy
    void onShutdown() {
        log.debug("CA service shutdown");
    }
}
