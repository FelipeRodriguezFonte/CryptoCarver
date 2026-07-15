package com.cryptoforge.crypto.hsm;

import java.security.Key;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;

public class SimulatedHsmProvider {
    private static final SimulatedHsmProvider INSTANCE = new SimulatedHsmProvider();
    
    private final Map<String, KeyMaterial> keyStore = new ConcurrentHashMap<>();

    private SimulatedHsmProvider() { }

    public static SimulatedHsmProvider getInstance() {
        return INSTANCE;
    }

    public void importKey(KeyMaterial keyMaterial) {
        if (keyMaterial == null) throw new IllegalArgumentException("KeyMaterial cannot be null");
        keyStore.put(keyMaterial.getId(), keyMaterial);
    }

    public KeyMaterial getKeyMetadata(String id) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) return null;
        if (km.getExportability() == KeyExportability.NON_EXPORTABLE) {
            return km.withoutRawKey();
        }
        return km;
    }
    
    public Set<String> listKeyIds() {
        return Collections.unmodifiableSet(keyStore.keySet());
    }

    public Key exportKey(String id) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) throw new IllegalArgumentException("Key not found in HSM: " + id);
        if (km.getExportability() == KeyExportability.NON_EXPORTABLE) {
            throw new UnsupportedOperationException("Cannot export NON_EXPORTABLE key: " + id);
        }
        return km.getKey();
    }

    // internal usage
    private Key getUsableKey(String id, KeyUsage requiredUsage) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) throw new IllegalArgumentException("Key not found in HSM: " + id);
        if (!km.getUsages().contains(requiredUsage)) {
            throw new IllegalStateException("Key " + id + " does not support usage: " + requiredUsage);
        }
        return km.getKey();
    }

    public void clear() {
        for (KeyMaterial km : keyStore.values()) {
            Key k = km.getKey();
            if (k != null && k instanceof javax.security.auth.Destroyable) {
                try {
                    ((javax.security.auth.Destroyable) k).destroy();
                } catch (Exception ignored) { }
            } else if (k instanceof SecretKey) {
                // Best effort zeroization of known raw bytes if we could intercept, 
                // but standard SecretKeySpec doesn't expose byte modification safely.
            }
        }
        keyStore.clear();
    }
    
    public byte[] sign(String keyId, byte[] data, String algorithm) {
        // We will implement this by delegating to some crypto operations class or doing it directly
        // For now, we will expose the internal method to allow the specific operations to grab the key 
        // IF they are inside the cryptoforge crypto package, or we can just return the raw key for now
        // if we add a package-private getter, but HSM shouldn't even do that.
        // The safest design is for the HSM to actually DO the operation.
        throw new UnsupportedOperationException("Not implemented yet");
    }
    
    // For integration with existing modules without rewriting everything, 
    // we can provide a package-private method that allows our own crypto utilities 
    // (like MACOperations, SymmetricCipher) to retrieve the raw Key if they verify the usage.
    
    public Key getRawKeyForInternalUse(String id, KeyUsage requiredUsage) {
        return getUsableKey(id, requiredUsage);
    }
}
