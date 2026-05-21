package com.gijela.morpheus.security.auth;

import com.gijela.morpheus.security.config.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 从 Redis Set 读取权限: key = prefix + username */
public class RedisAuthoritiesProvider implements AuthoritiesProvider {
    private static final Logger log = LoggerFactory.getLogger(RedisAuthoritiesProvider.class);
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties props;

    public RedisAuthoritiesProvider(StringRedisTemplate redisTemplate, JwtProperties props) {
        this.redisTemplate = redisTemplate;
        this.props = props;
    }

    @Override
    public Collection<? extends GrantedAuthority> load(String username, Long userId) {
        try {
            String key = props.getRedisAuthorityKeyPrefix() + username;
            Set<String> members = redisTemplate.opsForSet().members(key);
            if (members == null || members.isEmpty()) {
                return Collections.emptyList();
            }
            return members.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("加载 Redis 权限失败 username={}: {}", username, e.getMessage());
            return Collections.emptyList();
        }
    }
}
