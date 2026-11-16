package com.campus.convert.security;

import com.campus.convert.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "test-secret-key-for-campusformat-0123456789abcdef", 12, 10);

    @Test
    void generateAndParseDownloadToken() {
        String token = jwtUtil.generateDownloadToken(7L, 42L);

        JwtUtil.DownloadToken claims = jwtUtil.parseDownloadToken(token);

        assertEquals(Long.valueOf(7L), claims.userId());
        assertEquals(Long.valueOf(42L), claims.fileId());
        assertNotNull(claims.jti());
    }

    @Test
    void rejectNormalTokenAsDownloadToken() {
        String loginToken = jwtUtil.generateToken(7L, "student", "STUDENT");

        assertThrows(BizException.class, () -> jwtUtil.parseDownloadToken(loginToken));
    }

    @Test
    void rejectTamperedDownloadToken() {
        String token = jwtUtil.generateDownloadToken(7L, 42L);
        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertThrows(BizException.class, () -> jwtUtil.parseDownloadToken(tampered));
    }
}
