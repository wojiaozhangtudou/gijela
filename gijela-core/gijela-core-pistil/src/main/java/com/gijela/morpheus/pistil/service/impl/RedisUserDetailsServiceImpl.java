package com.gijela.morpheus.pistil.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis 缓存的 UserDetailsService：
 * - 优先从 Redis(key: {keyPrefix}{username}) 读取缓存的 UserDetails
 * - 缓存未命中时委托 DbUserDetailsServiceImpl 加载并缓存（TTL 可配置）
 */
@Service
@Primary
public class RedisUserDetailsServiceImpl implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(RedisUserDetailsServiceImpl.class);

    // 从配置读取前缀与 TTL（分钟）
    @Value("${security.cache.user.key-prefix:security:user:}")
    private String keyPrefix;

    @Value("${security.cache.user.ttl-minutes:10}")
    private long ttlMinutes;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 不从 Spring 容器注入 ObjectMapper，内部创建一个独立实例以避免自动装配问题
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper m = new ObjectMapper();
        // 自动注册可用模块（例如 JavaTimeModule）以增强类型支持
        m.findAndRegisterModules();
        // 忽略未知字段，提升反序列化鲁棒性
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return m;
    }

    @Autowired
    private DbUserDetailsServiceImpl dbUserDetailsService; // 保留原有 DB 实现并委托

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("用户名为空");
        }
        String key = buildKey(username);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                CachedUser cu = OBJECT_MAPPER.readValue(json, CachedUser.class);
                return buildUserDetailsFromCached(cu);
            }
        } catch (Exception e) {
            // 读取或反序列化失败时记录日志并回退到 DB 加载
            logger.warn(
                    "Failed to read/deserialize cached user for {} (redisKey={}): {}",
                    username,
                    key,
                    e.toString()
            );
        }
        // 缓存未命中或反序列化失败，委托 DB
        UserDetails ud = dbUserDetailsService.loadUserByUsername(username);
        // 缓存到 Redis
        try {
            CachedUser cu = CachedUser.fromUserDetails(ud);
            String json = OBJECT_MAPPER.writeValueAsString(cu);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
        } catch (JsonProcessingException e) {
            // 记录缓存失败的情况
            logger.warn(
                    "Failed to serialize/cache UserDetails for {} (redisKey={}): {}",
                    username,
                    key,
                    e.toString()
            );
        } catch (Exception e) {
            // 其他 Redis 写入异常
            logger.warn(
                    "Failed to write cached user to Redis for {} (redisKey={}): {}",
                    username,
                    key,
                    e.toString()
            );
        }
        return ud;
    }

    private String buildKey(String username) {
        return keyPrefix + username;
    }

    private UserDetails buildUserDetailsFromCached(CachedUser cu) {
        List<GrantedAuthority> auths = cu.getAuthorities().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        User.UserBuilder b = User.withUsername(cu.getUsername())
                .password(cu.getPassword() == null ? "" : cu.getPassword())
                .authorities(auths);
        b.accountExpired(cu.isAccountExpired());
        b.accountLocked(cu.isAccountLocked());
        b.credentialsExpired(cu.isCredentialsExpired());
        b.disabled(cu.isDisabled());
        return b.build();
    }

    // 简化的缓存模型
    public static class CachedUser {
        private String username;
        private String password;
        private boolean disabled;
        private boolean accountExpired;
        private boolean accountLocked;
        private boolean credentialsExpired;
        private List<String> authorities = new ArrayList<>();

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isDisabled() {
            return disabled;
        }

        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }

        public boolean isAccountExpired() {
            return accountExpired;
        }

        public void setAccountExpired(boolean accountExpired) {
            this.accountExpired = accountExpired;
        }

        public boolean isAccountLocked() {
            return accountLocked;
        }

        public void setAccountLocked(boolean accountLocked) {
            this.accountLocked = accountLocked;
        }

        public boolean isCredentialsExpired() {
            return credentialsExpired;
        }

        public void setCredentialsExpired(boolean credentialsExpired) {
            this.credentialsExpired = credentialsExpired;
        }

        public List<String> getAuthorities() {
            return authorities;
        }

        public void setAuthorities(List<String> authorities) {
            this.authorities = authorities;
        }

        public static CachedUser fromUserDetails(UserDetails ud) {
            CachedUser cu = new CachedUser();
            cu.setUsername(ud.getUsername());
            cu.setPassword(ud.getPassword());
            cu.setDisabled(!ud.isEnabled());
            cu.setAccountExpired(!ud.isAccountNonExpired());
            cu.setAccountLocked(!ud.isAccountNonLocked());
            cu.setCredentialsExpired(!ud.isCredentialsNonExpired());
            cu.setAuthorities(
                    ud.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList())
            );
            return cu;
        }
    }
}
