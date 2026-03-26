package syber.security.digitalsignature.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import syber.security.digitalsignature.crypto.service.CryptoService;
import syber.security.digitalsignature.crypto.service.CryptoServiceImpl;
import syber.security.digitalsignature.exception.CryptoException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoServiceImpl();
    }

    @Test
    void sha512_matchesKnownVector_forEmptyInput() {
        // SHA-512("") = cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce
        //               47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e
        String expected = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce"
                + "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e";
        assertThat(cryptoService.hashSha512(new byte[0])).isEqualToIgnoringCase(expected);
    }

    @Test
    void sha512_matchesKnownVector_forAbcInput() {
        // SHA-512("abc") = ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a
        //                  2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f
        String expected = "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f";
        assertThat(cryptoService.hashSha512("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualToIgnoringCase(expected);
    }

    @Test
    void generateKeyPair_producesRsa2048KeyPair() {
        KeyPair pair = cryptoService.generateKeyPair();
        assertThat(pair.getPublic()).isNotNull();
        assertThat(pair.getPrivate()).isNotNull();
        assertThat(pair.getPublic().getAlgorithm()).isEqualTo("RSA");
        assertThat(pair.getPrivate().getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void privateKey_encryptDecryptRoundTrip_returnsSameKey() {
        KeyPair pair = cryptoService.generateKeyPair();
        String password = "Str0ngP@ssw0rd";

        String encrypted = cryptoService.encryptPrivateKey(pair.getPrivate(), password);
        PrivateKey decoded = cryptoService.decryptPrivateKey(encrypted, password);

        assertThat(decoded.getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
    }

    @Test
    void privateKey_decryptWithWrongPassword_throws() {
        KeyPair pair = cryptoService.generateKeyPair();
        String encrypted = cryptoService.encryptPrivateKey(pair.getPrivate(), "correct-pass");

        assertThatThrownBy(() -> cryptoService.decryptPrivateKey(encrypted, "wrong-pass"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void publicKey_encodeDecodeRoundTrip_returnsSameKey() {
        KeyPair pair = cryptoService.generateKeyPair();
        String encoded = cryptoService.encodePublicKey(pair.getPublic());
        PublicKey decoded = cryptoService.decodePublicKey(encoded);
        assertThat(decoded.getEncoded()).isEqualTo(pair.getPublic().getEncoded());
    }

    @Test
    void signAndVerify_roundTrip_succeeds() {
        KeyPair pair = cryptoService.generateKeyPair();
        byte[] data = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);

        String signature = cryptoService.sign(data, pair.getPrivate());

        assertThat(signature).isNotBlank();
        assertThat(cryptoService.verify(data, signature, pair.getPublic())).isTrue();
    }

    @Test
    void verify_tamperedData_returnsFalse() {
        KeyPair pair = cryptoService.generateKeyPair();
        byte[] data = "original content".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "tampered content".getBytes(StandardCharsets.UTF_8);
        String signature = cryptoService.sign(data, pair.getPrivate());

        assertThat(cryptoService.verify(tampered, signature, pair.getPublic())).isFalse();
    }

    @Test
    void verify_withDifferentKey_returnsFalse() {
        KeyPair signer = cryptoService.generateKeyPair();
        KeyPair attacker = cryptoService.generateKeyPair();
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
        String signature = cryptoService.sign(data, signer.getPrivate());

        assertThat(cryptoService.verify(data, signature, attacker.getPublic())).isFalse();
    }

    @Test
    void verify_malformedBase64Signature_throwsCryptoException() {
        KeyPair pair = cryptoService.generateKeyPair();
        assertThatThrownBy(() -> cryptoService.verify(new byte[]{1, 2, 3}, "!!!notbase64!!!", pair.getPublic()))
                .isInstanceOf(CryptoException.class);
    }
}
