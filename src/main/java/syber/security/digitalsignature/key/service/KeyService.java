package syber.security.digitalsignature.key.service;

import syber.security.digitalsignature.key.dto.CreateKeyRequest;
import syber.security.digitalsignature.key.entity.KeyPair;

import java.util.List;

public interface KeyService {

    KeyPair createKey(String email, CreateKeyRequest request);

    List<KeyPair> listKeys(String email);

    KeyPair getKey(String email, Long keyId);

    KeyPair setDefault(String email, Long keyId);

    KeyPair revokeKey(String email, Long keyId);

    void deleteKey(String email, Long keyId);
}
