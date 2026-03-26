package syber.security.digitalsignature.crypto.util;

import syber.security.digitalsignature.exception.CryptoException;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    public static final String ALGORITHM = "SHA-512";
    private static final int BUFFER_SIZE = 8192;

    private HashUtil() {
    }

    public static String sha512Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(data);
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("SHA-512 algorithm not available", e);
        }
    }

    public static String sha512Hex(InputStream input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("SHA-512 algorithm not available", e);
        } catch (Exception e) {
            throw new CryptoException("Failed to hash input stream", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
