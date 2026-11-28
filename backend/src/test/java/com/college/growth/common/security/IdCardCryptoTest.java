package com.college.growth.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 身份证 AES-256-GCM / SHA-256 单测。 */
class IdCardCryptoTest {

    private static final String PLAIN = "320102200201011232";

    @Test
    @DisplayName("encrypt and decrypt round trip")
    void roundTrip() {
        String cipher = IdCardCrypto.encrypt(PLAIN);
        assertTrue(IdCardCrypto.isEncrypted(cipher));
        assertNotEquals(PLAIN, cipher);
        assertEquals(PLAIN, IdCardCrypto.decrypt(cipher));
    }

    @Test
    @DisplayName("same plaintext produces different ciphertext (random IV)")
    void randomIv() {
        assertNotEquals(IdCardCrypto.encrypt(PLAIN), IdCardCrypto.encrypt(PLAIN));
    }

    @Test
    @DisplayName("hash is stable and 64 hex chars")
    void hashStable() {
        String hash = IdCardCrypto.hash(PLAIN);
        assertEquals(64, hash.length());
        assertEquals(hash, IdCardCrypto.hash(PLAIN));
        assertFalse(IdCardCrypto.isEncrypted(hash));
    }

    @Test
    @DisplayName("legacy plaintext tolerated")
    void legacyPlaintext() {
        assertEquals(PLAIN, IdCardCrypto.decrypt(PLAIN));
        assertEquals(PLAIN, IdCardCrypto.plain(PLAIN, null));
        assertEquals(PLAIN, IdCardCrypto.plain(null, IdCardCrypto.encrypt(PLAIN)));
    }
}
