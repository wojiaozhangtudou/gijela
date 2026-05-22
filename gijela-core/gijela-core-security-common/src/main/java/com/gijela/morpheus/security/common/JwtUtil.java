package com.gijela.morpheus.security.common;

import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.security.config.JwtProperties;
import com.gijela.morpheus.common.enums.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

/** 公共 JWT 工具 */
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 60L;
    @Autowired private JwtProperties jwtProperties;
    private volatile SecretKey signingKey;

    private long getAccessTtlMillis() {
        int minutes = jwtProperties == null ? 0 : jwtProperties.getAccessExpMinutes();
        return minutes * 60_000L;
    }

    private long getRefreshTtlMillis() {
        int days = jwtProperties == null ? 0 : jwtProperties.getRefreshExpDays();
        return days * 24L * 60L * 60L * 1000L;
    }

    private SecretKey getSigningKey() {
        if (signingKey != null) return signingKey;
        synchronized (this) {
            if (signingKey != null) return signingKey;
            String secret = jwtProperties == null ? null : jwtProperties.getSecret();
            byte[] keyBytes;
            if (secret == null || secret.isBlank()) {
                logger.warn("security.jwt.secret 未配置或为空，临时生成随机密钥 (HS512)。"
                        + "请配置持久化 Base64 64 字节+ 密钥避免重启失效。");
                signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
                return signingKey;
            }
            try {
                keyBytes = Decoders.BASE64.decode(secret);
            } catch (Exception e) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
            if (keyBytes.length < 64) {
                logger.warn("配置的 security.jwt.secret 长度不足 ({} bytes) 不满足 HS512 >=64 bytes, "
                        + "将用 SHA-512 派生。", keyBytes.length);
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-512");
                    byte[] derived = md.digest(secret.getBytes(StandardCharsets.UTF_8));
                    signingKey = Keys.hmacShaKeyFor(derived);
                    return signingKey;
                } catch (NoSuchAlgorithmException ex) {
                    logger.error("无法获取 SHA-512: {}", ex.getMessage());
                    signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
                    return signingKey;
                }
            }
            signingKey = Keys.hmacShaKeyFor(keyBytes);
            return signingKey;
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        Claims claims = getAllClaimsFromToken(token);
        return claims == null ? null : claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            logger.warn("JWT 已过期: {}", e.getMessage());
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("JWT解析失败: {}", e.getMessage());
            return null;
        }
    }

    private Boolean isTokenExpired(String token) {
        Date expiration = getExpirationDateFromToken(token);
        return expiration != null && expiration.before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername(), getAccessTtlMillis());
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("typ", "refresh");
        claims.put("jti", UUID.randomUUID().toString());
        return createToken(claims, userDetails.getUsername(), getRefreshTtlMillis());
    }

    public String generateAccessToken(Long userId, String username, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("typ", "access");
        claims.put("username", username);
        claims.put("jti", UUID.randomUUID().toString());
        if (extraClaims != null) {
            claims.putAll(extraClaims);
        }
        return createTokenWithSubject(claims, String.valueOf(userId), getAccessTtlMillis());
    }

    public String generateRefreshToken(Long userId) {
        return generateRefreshToken(userId, null);
    }

    public String generateRefreshToken(Long userId, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("typ", "refresh");
        claims.put("jti", UUID.randomUUID().toString());
        if (sessionId != null && !sessionId.isBlank()) {
            claims.put("sid", sessionId);
        }
        return createTokenWithSubject(claims, String.valueOf(userId), getRefreshTtlMillis());
    }

    public String getJti(String token) {
        Claims claims = parseToken(token);
        return getJti(claims);
    }

    public String getJti(Claims claims) {
        if (claims == null) return null;
        Object jti = claims.get("jti");
        if (jti != null) return jti.toString();
        return claims.getId();
    }

    private String createToken(Map<String, Object> claims, String subject, long ttlMillis) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttlMillis);
        JwtBuilder builder = Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512);
        if (claims != null) {
            claims.forEach(builder::claim);
        }
        return builder.compact();
    }

    private String createTokenWithSubject(Map<String, Object> claims, String subject, long ttlMillis) {
        return createToken(claims, subject, ttlMillis);
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            String username = getUsernameFromToken(token);
            return username != null
                    && username.equals(userDetails.getUsername())
                    && !isTokenExpired(token);
        } catch (Exception e) {
            logger.error("Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    public Boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("Token 已过期: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("Token格式验证失败: {}", e.getMessage());
            return false;
        }
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            logger.warn("解析 token 失败 - 已过期: {}", e.getMessage());
            throw new BizException(ErrorCode.TOKEN_INVALID, "令牌已过期");
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("解析 token 失败: {}", e.getMessage());
            throw new BizException(ErrorCode.TOKEN_INVALID, "令牌无效");
        }
    }

    public Boolean isRefreshToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return claims != null && "refresh".equals(claims.get("typ"));
        } catch (Exception e) {
            logger.debug("判断 refresh token 异常: {}", e.getMessage());
            return false;
        }
    }

    public long accessExpireAt() {
        return System.currentTimeMillis() + getAccessTtlMillis();
    }

    public long refreshExpireAt() {
        return System.currentTimeMillis() + getRefreshTtlMillis();
    }
}


