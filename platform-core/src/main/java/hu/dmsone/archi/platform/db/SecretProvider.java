package hu.dmsone.archi.platform.db;

import java.util.Optional;

/**
 * Titoktár absztrakció. GCP-n Secret Manager, on-prem Vault vagy Kubernetes Secret mögé is tehető;
 * az MVP implementáció a platform adatbázisban AES-GCM-mel titkosítva tárol.
 */
public interface SecretProvider {
    Optional<String> get(String key);

    void put(String key, String value);

    void delete(String key);
}
