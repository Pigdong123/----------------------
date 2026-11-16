package com.campus.convert.security;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMillis;
    private final long downloadTtlMillis;

    public JwtUtil(@Value("${campus.jwt.secret}") String secret,
                   @Value("${campus.jwt.ttl-hours}") long ttlHours,
                   @Value("${campus.storage.download-token-ttl-minutes:10}") long downloadTtlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlHours * 60 * 60 * 1000L;
        this.downloadTtlMillis = Math.max(1, downloadTtlMinutes) * 60 * 1000L;
    }

    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 生成短期下载令牌：绑定用户与产物文件，并带 jti 供一次性消费。
     */
    public String generateDownloadToken(Long userId, Long fileId) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("fileId", fileId)
                .claim("purpose", "download")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + downloadTtlMillis))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "令牌无效或已过期");
        }
    }

    /**
     * 解析并校验下载令牌，返回用户、产物与一次性消费标识。
     */
    public DownloadToken parseDownloadToken(String token) {
        Claims claims = parseToken(token);
        if (!"download".equals(claims.get("purpose", String.class))) {
            throw new BizException(ResultCode.DOWNLOAD_FORBIDDEN, "下载令牌用途不匹配");
        }
        Object rawFileId = claims.get("fileId");
        if (rawFileId == null || claims.getId() == null || claims.getSubject() == null) {
            throw new BizException(ResultCode.DOWNLOAD_FORBIDDEN, "下载令牌无效");
        }
        return new DownloadToken(
                Long.valueOf(claims.getSubject()),
                ((Number) rawFileId).longValue(),
                claims.getId());
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    public record DownloadToken(Long userId, Long fileId, String jti) {
    }
}
