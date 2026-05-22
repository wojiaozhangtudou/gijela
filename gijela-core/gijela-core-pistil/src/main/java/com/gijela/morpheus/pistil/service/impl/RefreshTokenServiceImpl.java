package com.gijela.morpheus.pistil.service.impl;

import com.gijela.morpheus.pistil.domain.vo.LoginSessionVO;
import com.gijela.morpheus.pistil.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${security.jwt.refresh-key-prefix:security:refresh:}")
    private String refreshKeyPrefix;

    @Value("${security.jwt.refresh-session-index-prefix:security:user:sessions:}")
    private String refreshSessionIndexPrefix;

    @Override
    public String createSession(Long userId, String username, String refreshJti, long expireAt, String clientLabel, String deviceNo, String loginIp) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        writeSession(sid, userId, username, refreshJti, expireAt, clientLabel, deviceNo, loginIp, System.currentTimeMillis());
        try {
            redisTemplate.opsForSet().add(indexKey(userId), sid);
        } catch (Exception e) {
            log.warn("写入用户会话索引失败 userId={} sid={} err={}", userId, sid, e.getMessage());
        }
        return sid;
    }

    @Override
    public boolean validateSession(String sessionId, String refreshJti) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(refreshJti)) return false;
        try {
            Map<Object, Object> m = redisTemplate.opsForHash().entries(sessionKey(sessionId));
            if (m == null || m.isEmpty()) return false;
            Object stored = m.get("refreshJtiHash");
            return stored != null && stored.toString().equals(sha256(refreshJti));
        } catch (Exception e) {
            log.warn("校验会话失败 sid={} err={}", sessionId, e.getMessage());
            return false;
        }
    }

    @Override
    public void refreshSession(String sessionId, String refreshJti, long expireAt) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(refreshJti)) return;
        try {
            String key = sessionKey(sessionId);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) return;
            long now = System.currentTimeMillis();
            redisTemplate.opsForHash().put(key, "refreshJtiHash", sha256(refreshJti));
            redisTemplate.opsForHash().put(key, "lastActiveAt", String.valueOf(now));
            long ttl = Math.max(1L, expireAt - now);
            redisTemplate.expire(key, ttl, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("刷新会话失败 sid={} err={}", sessionId, e.getMessage());
        }
    }

    @Override
    public void touchSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) return;
        try {
            String key = sessionKey(sessionId);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) return;
            redisTemplate.opsForHash().put(key, "lastActiveAt", String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            log.debug("更新会话活跃时间失败 sid={} err={}", sessionId, e.getMessage());
        }
    }

    @Override
    public void revokeSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) return;
        try {
            String sk = sessionKey(sessionId);
            Object uidObj = redisTemplate.opsForHash().get(sk, "userId");
            redisTemplate.delete(sk);
            if (uidObj != null) {
                Long uid = Long.parseLong(uidObj.toString());
                redisTemplate.opsForSet().remove(indexKey(uid), sessionId);
            }
        } catch (Exception e) {
            log.warn("撤销会话失败 sid={} err={}", sessionId, e.getMessage());
        }
    }

    @Override
    public void revokeAllSessions(Long userId) {
        if (userId == null) return;
        try {
            String idx = indexKey(userId);
            Set<String> sids = redisTemplate.opsForSet().members(idx);
            if (sids != null && !sids.isEmpty()) {
                for (String sid : sids) {
                    redisTemplate.delete(sessionKey(sid));
                }
            }
            redisTemplate.delete(idx);
        } catch (Exception e) {
            log.warn("撤销用户全部会话失败 userId={} err={}", userId, e.getMessage());
        }
    }

    @Override
    public List<LoginSessionVO> listSessions(Long userId) {
        if (userId == null) return List.of();
        List<LoginSessionVO> list = new ArrayList<>();
        List<String> invalidSids = new ArrayList<>();
        try {
            Set<String> sids = redisTemplate.opsForSet().members(indexKey(userId));
            if (sids == null || sids.isEmpty()) return list;
            for (String sid : sids) {
                Map<Object, Object> m = redisTemplate.opsForHash().entries(sessionKey(sid));
                if (m == null || m.isEmpty()) {
                    invalidSids.add(sid);
                    continue;
                }
                list.add(toSessionVo(sid, m));
            }
            cleanupInvalidIndexedSessions(userId, invalidSids);
        } catch (Exception e) {
            log.warn("查询会话列表失败 userId={} err={}", userId, e.getMessage());
        }
        list.sort(Comparator.comparing(LoginSessionVO::getLastActiveAt, Comparator.nullsLast(Long::compareTo)).reversed());
        return list;
    }

    @Override
    public List<LoginSessionVO> listAllSessions() {
        List<LoginSessionVO> list = new ArrayList<>();
        try {
            Set<String> keys = redisTemplate.keys(refreshKeyPrefix + "session:*");
            if (keys == null || keys.isEmpty()) return list;
            for (String key : keys) {
                Map<Object, Object> m = redisTemplate.opsForHash().entries(key);
                if (m == null || m.isEmpty()) continue;
                String sid = key.substring((refreshKeyPrefix + "session:").length());
                list.add(toSessionVo(sid, m));
            }
        } catch (Exception e) {
            log.warn("查询全部会话失败 err={}", e.getMessage());
        }
        list.sort(Comparator.comparing(LoginSessionVO::getLastActiveAt, Comparator.nullsLast(Long::compareTo)).reversed());
        return list;
    }

    private LoginSessionVO toSessionVo(String sid, Map<Object, Object> m) {
        LoginSessionVO vo = new LoginSessionVO();
        vo.setSessionId(sid);
        vo.setUserId(parseLong(m.get("userId")));
        vo.setUsername(asString(m.get("username")));
        vo.setLoginAt(parseLong(m.get("loginAt")));
        vo.setLastActiveAt(parseLong(m.get("lastActiveAt")));
        vo.setClientLabel(asString(m.get("clientLabel")));
        vo.setDeviceNo(asString(m.get("deviceNo")));
        vo.setLoginIp(asString(m.get("loginIp")));
        return vo;
    }

    private void writeSession(String sessionId,
                              Long userId,
                              String username,
                              String refreshJti,
                              long expireAt,
                              String clientLabel,
                              String deviceNo,
                              String loginIp,
                              long now) {
        String key = sessionKey(sessionId);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("userId", String.valueOf(userId));
        m.put("username", username == null ? "" : username);
        m.put("loginAt", String.valueOf(now));
        m.put("lastActiveAt", String.valueOf(now));
        m.put("clientLabel", clientLabel == null ? "" : clientLabel);
        m.put("deviceNo", deviceNo == null ? "" : deviceNo);
        m.put("loginIp", loginIp == null ? "" : loginIp);
        m.put("refreshJtiHash", sha256(refreshJti));
        redisTemplate.opsForHash().putAll(key, m);
        long ttl = Math.max(1L, expireAt - now);
        redisTemplate.expire(key, ttl, TimeUnit.MILLISECONDS);
    }

    private void cleanupInvalidIndexedSessions(Long userId, List<String> invalidSids) {
        if (userId == null || invalidSids == null || invalidSids.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForSet().remove(indexKey(userId), invalidSids.toArray());
        } catch (Exception e) {
            log.debug("清理失效会话索引失败 userId={} sids={} err={}", userId, invalidSids, e.getMessage());
        }
    }

    private String sessionKey(String sid) {
        return refreshKeyPrefix + "session:" + sid;
    }

    private String indexKey(Long userId) {
        return refreshSessionIndexPrefix + userId;
    }

    private String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return raw;
        }
    }

    private Long parseLong(Object v) {
        if (v == null) return null;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private String asString(Object v) {
        return v == null ? null : v.toString();
    }
}
