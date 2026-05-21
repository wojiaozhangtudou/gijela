package com.gijela.morpheus.chat.llm.log.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * LLM 日志查询缓存服务
 * 用途：缓存热查询结果，减轻 ES 压力
 * 
 * 缓存策略：
 * - 聚合查询：5 分钟
 * - 详细记录：10 分钟
 * - 链路追踪：30 分钟
 */
@Slf4j
@Service
public class LlmLogCacheService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String CACHE_PREFIX = "llm:log:";
    private static final long DEFAULT_TTL = 5; // 5 分钟
    
    public LlmLogCacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 生成缓存 key
     */
    private String buildKey(String queryType, String queryParams) {
        return CACHE_PREFIX + queryType + ":" + queryParams;
    }
    
    /**
     * 从缓存读取
     */
    public String get(String queryType, String queryParams) {
        try {
            String key = buildKey(queryType, queryParams);
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                log.debug("Cache hit: {}", key);
                return value;
            }
        } catch (Exception e) {
            log.warn("Cache get failed", e);
        }
        return null;
    }
    
    /**
     * 写入缓存
     */
    public void put(String queryType, String queryParams, String value) {
        try {
            String key = buildKey(queryType, queryParams);
            redisTemplate.opsForValue().set(key, value, DEFAULT_TTL, TimeUnit.MINUTES);
            log.debug("Cache put: {}", key);
        } catch (Exception e) {
            log.warn("Cache put failed", e);
            // 缓存失败不影响业务逻辑
        }
    }
    
    /**
     * 写入缓存（带自定义过期时间）
     */
    public void put(String queryType, String queryParams, String value, long ttlMinutes) {
        try {
            String key = buildKey(queryType, queryParams);
            redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Cache put failed with custom TTL", e);
        }
    }
    
    /**
     * 删除缓存
     */
    public void delete(String queryType, String queryParams) {
        try {
            String key = buildKey(queryType, queryParams);
            redisTemplate.delete(key);
            log.debug("Cache deleted: {}", key);
        } catch (Exception e) {
            log.warn("Cache delete failed", e);
        }
    }
    
    /**
     * 清空所有 LLM 日志缓存
     */
    public void clearAll() {
        try {
            var cursor = redisTemplate.scan(
                org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(CACHE_PREFIX + "*")
                    .count(100)
                    .build()
            );
            
            while (cursor.hasNext()) {
                String key = cursor.next();
                redisTemplate.delete(key);
            }
            log.info("All LLM log cache cleared");
        } catch (Exception e) {
            log.warn("Cache clear all failed", e);
        }
    }
}
