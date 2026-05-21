package com.gijela.morpheus.pistil.validator;

import com.gijela.morpheus.security.token.TokenVersionValidator;
import com.gijela.morpheus.pistil.service.TokenVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认的 TokenVersionValidator 实现：使用 TokenVersionService（Redis 优先，回落 DB）进行校验。
 * 当项目中已有其他实现（例如 RedisTokenVersionValidator）时，本实现不会生效。
 */
@Component
@ConditionalOnMissingBean(TokenVersionValidator.class)
public class DefaultTokenVersionValidator implements TokenVersionValidator {

    private static final Logger log = LoggerFactory.getLogger(DefaultTokenVersionValidator.class);

    @Autowired
    private TokenVersionService tokenVersionService;

    @Override
    public boolean isValid(String username, Integer tokenVersionInToken) {
        if (username == null) {
            return false;
        }
        try {
            Integer cur = tokenVersionService.get(username);
            if (cur == null) {
                cur = tokenVersionService.ensureInitialized(username, null);
            }
            if (tokenVersionInToken != null && cur != null && !tokenVersionInToken.equals(cur)) {
                log.debug("tokenVersion mismatch for {}: token={}, current={}", username, tokenVersionInToken, cur);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("DefaultTokenVersionValidator encountered exception for {}: {}", username, e.toString());
            return true;
        }
    }
}
