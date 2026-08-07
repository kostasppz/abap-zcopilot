package com.abapguardian.eclipse.security;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

import java.util.Optional;

/**
 * Abstraction over Eclipse secure storage for any future credentials
 * (e.g. an API key for an explicitly-enabled external AI provider).
 *
 * <p>Credentials are NEVER written to normal preferences, workspace
 * metadata or logs. Hosted provider credentials remain on the server and
 * are never distributed with the plug-in.
 */
public class SecureCredentialStore {

    private static final String NODE = "com.abapguardian.eclipse";

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
}
