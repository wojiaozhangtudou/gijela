package com.gijela.morpheus.pistil.service.impl;

import com.gijela.morpheus.pistil.service.TokenVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenVersionServiceImpl implements TokenVersionService {

    private static final Logger log = LoggerFactory.getLogger(TokenVersionServiceImpl.class);

    @Autowired private StringRedisTemplate redisTemplate;
    @Value("${security.jwt.user-version-key-prefix:security:user:}")
    private String userVersionKeyPrefix;

    private String key(String username){
        return userVersionKeyPrefix + username + ":version";
    }

    @Override
    public Integer get(String username) {
        try {
            String v = redisTemplate.opsForValue().get(key(username));
            return v == null ? null : Integer.parseInt(v);
        } catch (Exception e) {
            log.warn("读取 tokenVersion 失败, username={}, err={}", username, e.getMessage());
            return null;
        }
    }

    @Override
    public Integer ensureInitialized(String username, Integer initial) {
        Integer cur = get(username);
        if (cur != null) return cur;
        int init = initial==null?0:initial;
        try {
            redisTemplate.opsForValue().set(key(username), String.valueOf(init));
        } catch (Exception e) {
            log.warn("初始化 tokenVersion 失败, username={}, init={}, err={}", username, init, e.getMessage());
        }
        return init;
    }

    @Override
    public Integer increment(String username) {
        try {
            Long v = redisTemplate.opsForValue().increment(key(username));
            return v==null?null:v.intValue();
        } catch (Exception e){
            Integer cur = get(username); int next = (cur==null?0:cur)+1;
            try {
                redisTemplate.opsForValue().set(key(username), String.valueOf(next));
            } catch (Exception ex) {
                log.error("递增 tokenVersion 失败且回写失败, username={}, next={}, err={}", username, next, ex.getMessage());
            }
            return next;
        }
    }
}
