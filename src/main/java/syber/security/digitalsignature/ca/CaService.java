package syber.security.digitalsignature.ca;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.List;

public interface CaService {

    X509Certificate issueCertificate(PublicKey subjectPublicKey, String cn, String email);

    X509Certificate getCaCertificate();

    PrivateKey getCaPrivateKey();

    X509CRL buildCrl(List<RevokedEntry> revokedEntries);

    record RevokedEntry(BigInteger serial, java.util.Date revocationDate) {}
}
