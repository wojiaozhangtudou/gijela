package com.gijela.morpheus.chat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenCipherTest {

    @Test
    void roundTrip_shouldRecoverPlaintext_withDevDefaultKey() {
        TokenCipher cipher = new TokenCipher(null);
        String plain = "sk-1234567890abcdef-test-token";
        String c1 = cipher.encrypt(plain);
        String c2 = cipher.encrypt(plain);
        // 每次 IV 随机 → 密文不同
        assertNotEquals(c1, c2);
        assertEquals(plain, cipher.decrypt(c1));
        assertEquals(plain, cipher.decrypt(c2));
    }

    @Test
    void roundTrip_shouldWork_withExplicitBase64Key() {
        // 32-byte key in base64
        String key = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU=";
        TokenCipher cipher = new TokenCipher(key);
        String plain = "hello-mcp-token";
        String enc = cipher.encrypt(plain);
        assertEquals(plain, cipher.decrypt(enc));
    }

    @Test
    void differentKeys_shouldNotCrossDecrypt() {
        TokenCipher c1 = new TokenCipher("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU=");
        TokenCipher c2 = new TokenCipher("MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwYWI=");
        String enc = c1.encrypt("secret");
        assertThrows(IllegalStateException.class, () -> c2.decrypt(enc));
    }

    @Test
    void edgeCases_handleNullAndEmpty() {
        TokenCipher cipher = new TokenCipher(null);
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
        assertEquals("", cipher.encrypt(""));
        assertEquals("", cipher.decrypt(""));
    }

    @Test
    void mask_shouldShowOnlyLast4() {
        assertEquals("***cdef", TokenCipher.mask("abcdef"));
        assertEquals("***", TokenCipher.mask("abc"));
        assertNull(TokenCipher.mask(null));
        assertNull(TokenCipher.mask(""));
    }

    @Test
    void invalidKey_shouldThrow() {
        assertThrows(IllegalStateException.class,
                () -> new TokenCipher("not-base64!!!"));
    }

    @Test
    void invalidKeyLength_shouldThrow() {
        // base64 of 5 bytes (not 16/24/32)
        String shortKey = java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5});
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new TokenCipher(shortKey));
        assertNotNull(ex.getMessage());
    }
}
