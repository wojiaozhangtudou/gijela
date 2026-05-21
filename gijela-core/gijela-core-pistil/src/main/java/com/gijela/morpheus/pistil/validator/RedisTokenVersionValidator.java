package com.gijela.morpheus.pistil.validator;

import com.gijela.morpheus.security.token.TokenVersionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 基于 Redis 的 tokenVersion 校验实现 */
@Component
public class RedisTokenVersionValidator implements TokenVersionValidator {
    private static final Logger log = LoggerFactory.getLogger(RedisTokenVersionValidator.class);
    @Autowired private StringRedisTemplate redisTemplate;
    @Value("${security.jwt.enable-token-version-check:false}")
    private boolean enableTokenVersionCheck;
    @Value("${security.jwt.user-version-key-prefix:security:user:}")
    private String userVersionKeyPrefix;

    @Override
    public boolean isValid(String username, Integer tokenVersionInToken) {
        if (!enableTokenVersionCheck) return true;
        if (username == null || username.isBlank()) return false;
        if (tokenVersionInToken == null) return false;
        try {
            String key = userVersionKeyPrefix + username + ":version";
            String v = redisTemplate.opsForValue().get(key);
            if (v == null) {
                return tokenVersionInToken == 0; // 未初始化时允许 0
            }
            Integer cur = Integer.parseInt(v);
            boolean ok = tokenVersionInToken.equals(cur);
            if (!ok) {
                log.debug("tokenVersion mismatch username={} token={} redis={}", username, tokenVersionInToken, cur);
            }
            return ok;
        } catch (Exception e) {
            log.warn("tokenVersion 校验异常 username={} err={}", username, e.getMessage());
            return false;
        }
    }
}
