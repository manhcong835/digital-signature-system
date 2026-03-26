package syber.security.digitalsignature.crypto.service;

import org.springframework.stereotype.Service;
import syber.security.digitalsignature.crypto.util.EncryptionUtil;
import syber.security.digitalsignature.crypto.util.HashUtil;
import syber.security.digitalsignature.crypto.util.KeyGeneratorUtil;
import syber.security.digitalsignature.exception.CryptoException;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

@Service
public class CryptoServiceImpl implements CryptoService {

    public static final String SIGNATURE_ALGORITHM = "SHA512withRSA";

    @Override
    public KeyPair generateKeyPair() {
        return KeyGeneratorUtil.generateRsaKeyPair();
    }

    @Override
    public String encodePublicKey(PublicKey publicKey) {
        return KeyGeneratorUtil.encodePublicKey(publicKey);
    }

    @Override
    public String encryptPrivateKey(PrivateKey privateKey, String password) {
        String encoded = KeyGeneratorUtil.encodePrivateKey(privateKey);
        return EncryptionUtil.encryptWithPassword(encoded, password);
    }

    @Override
    public PrivateKey decryptPrivateKey(String encryptedBase64, String password) {
        String decoded = EncryptionUtil.decryptWithPassword(encryptedBase64, password);
        return KeyGeneratorUtil.decodePrivateKey(decoded);
    }

    @Override
    public PublicKey decodePublicKey(String base64) {
        return KeyGeneratorUtil.decodePublicKey(base64);
    }

    @Override
    public String sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new CryptoException("Failed to sign data", e);
        }
    }

    @Override
    public boolean verify(byte[] data, String signatureBase64, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            throw new CryptoException("Failed to verify signature", e);
        }
    }

    @Override
    public String hashSha512(byte[] data) {
        return HashUtil.sha512Hex(data);
    }
}
