package syber.security.digitalsignature.crypto.service;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public interface CryptoService {

    KeyPair generateKeyPair();

    String encodePublicKey(PublicKey publicKey);

    String encryptPrivateKey(PrivateKey privateKey, String password);

    PrivateKey decryptPrivateKey(String encryptedBase64, String password);

    PublicKey decodePublicKey(String base64);

    String sign(byte[] data, PrivateKey privateKey);

    boolean verify(byte[] data, String signatureBase64, PublicKey publicKey);

    String hashSha512(byte[] data);
}
