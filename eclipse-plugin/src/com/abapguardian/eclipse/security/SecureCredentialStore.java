package com.abapguardian.eclipse.security;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

import java.util.Optional;

/**
 * Abstraction over Eclipse secure storage for the Guardian deployment token
 * and any future credentials.
 *
 * <p>Credentials are NEVER written to normal preferences, workspace
 * metadata or logs. Hosted provider credentials remain on the server and
 * are never distributed with the plug-in.
 */
public class SecureCredentialStore {

    private static final String NODE = "com.abapguardian.eclipse";
    public static final String KEY_GUARDIAN_API_TOKEN = "guardianApiToken";

    private final ISecurePreferences node;

    public SecureCredentialStore() {
        this(SecurePreferencesFactory.getDefault().node(NODE));
    }

    public SecureCredentialStore(ISecurePreferences node) {
        this.node = node;
    }

    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(node.get(key, null));
        } catch (StorageException e) {
            return Optional.empty();
        }
    }

    public void put(String key, String value) throws StorageException {
        node.put(key, value, true); // encrypted
    }

    public void remove(String key) {
        node.remove(key);
    }

    public Optional<String> getGuardianApiToken() {
        return get(KEY_GUARDIAN_API_TOKEN).filter(value -> !value.isBlank());
    }

    public void putGuardianApiToken(String value) throws StorageException {
        if (value == null || value.isBlank()) {
            remove(KEY_GUARDIAN_API_TOKEN);
        } else {
            put(KEY_GUARDIAN_API_TOKEN, value.trim());
        }
    }
}
