package com.gijela.morpheus.chat.util;

import com.gijela.morpheus.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlGuardTest {

    @Test
    void shouldAllowHttpsPublicHost() {
        assertDoesNotThrow(() -> UrlGuard.validate("https://api.example.com/mcp", false));
    }

    @Test
    void shouldAllowHttpLocalhost() {
        assertDoesNotThrow(() -> UrlGuard.validate("http://localhost:8080/mcp", false));
        assertDoesNotThrow(() -> UrlGuard.validate("http://127.0.0.1:9000/mcp", false));
    }

    @Test
    void shouldRejectHttpForNonLocal() {
        assertThrows(BizException.class,
                () -> UrlGuard.validate("http://api.example.com/mcp", false));
    }

    @Test
    void shouldRejectFileScheme() {
        assertThrows(BizException.class,
                () -> UrlGuard.validate("file:///etc/passwd", true));
    }

    @Test
    void shouldRejectMissingHost() {
        assertThrows(BizException.class,
                () -> UrlGuard.validate("https:///path", false));
    }

    @Test
    void shouldRejectPrivateIpWhenDisallowed() {
        assertThrows(BizException.class,
                () -> UrlGuard.validate("https://10.0.0.1/mcp", false));
        assertThrows(BizException.class,
                () -> UrlGuard.validate("https://192.168.1.1/mcp", false));
        assertThrows(BizException.class,
                () -> UrlGuard.validate("https://169.254.1.1/mcp", false));
    }

    @Test
    void shouldAllowPrivateIpWhenFlagOn() {
        assertDoesNotThrow(() -> UrlGuard.validate("https://10.0.0.1/mcp", true));
        assertDoesNotThrow(() -> UrlGuard.validate("https://192.168.0.5/mcp", true));
    }

    @Test
    void shouldRejectBlankAndNull() {
        assertThrows(BizException.class, () -> UrlGuard.validate(null, true));
        assertThrows(BizException.class, () -> UrlGuard.validate("   ", true));
    }
}
