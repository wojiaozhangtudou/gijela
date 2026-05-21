package com.gijela.morpheus.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gijela.morpheus.llm.sdk.skill.SkillSource;
import com.gijela.morpheus.chat.domain.entity.ChatSkillState;
import com.gijela.morpheus.chat.mapper.ChatSkillStateMapper;
import com.gijela.morpheus.chat.service.SkillStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MyBatisSkillStateService implements SkillStateService {

    private static final Logger log = LoggerFactory.getLogger(MyBatisSkillStateService.class);

    private final ChatSkillStateMapper mapper;

    /** key = tenantId|name → enabled。null/缺失视为 true（默认启用）。 */
    private final Map<String, Boolean> enabledCache = new ConcurrentHashMap<>();

    public MyBatisSkillStateService(ChatSkillStateMapper mapper) {
        this.mapper = mapper;
        loadCache();
    }

    private void loadCache() {
        try {
            List<ChatSkillState> all = mapper.selectList(null);
            for (ChatSkillState row : all) {
                enabledCache.put(cacheKey(row.getTenantId(), row.getName()),
                        row.getEnabled() == null ? Boolean.TRUE : row.getEnabled());
            }
            log.info("[skill-state] cache loaded, size={}", enabledCache.size());
        } catch (Exception e) {
            log.warn("[skill-state] cache load failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEnabled(String tenantId, String name) {
        if (name == null) return true;
        Boolean v = enabledCache.get(cacheKey(normalize(tenantId), name));
        return v == null || v;
    }

    @Override
    public synchronized void setEnabled(String tenantId, String name, boolean enabled, String operator) {
        String t = normalize(tenantId);
        ChatSkillState existed = findOne(t, name);
        if (existed == null) {
            ChatSkillState row = new ChatSkillState();
            row.setTenantId(t);
            row.setName(name);
            row.setEnabled(enabled);
            row.setSource(SkillSource.BUILTIN.name());
            row.setUpdatedBy(operator);
            row.setUpdatedAt(LocalDateTime.now());
            mapper.insert(row);
        } else {
            existed.setEnabled(enabled);
            existed.setUpdatedBy(operator);
            existed.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(existed);
        }
        enabledCache.put(cacheKey(t, name), enabled);
    }

    @Override
    public synchronized void recordLoadSuccess(String tenantId, Map<String, SkillSource> currentSources) {
        String t = normalize(tenantId);
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, SkillSource> entry : currentSources.entrySet()) {
            String name = entry.getKey();
            SkillSource src = entry.getValue();
            ChatSkillState existed = findOne(t, name);
            if (existed == null) {
                ChatSkillState row = new ChatSkillState();
                row.setTenantId(t);
                row.setName(name);
                row.setEnabled(true);
                row.setSource(src == null ? SkillSource.BUILTIN.name() : src.name());
                row.setLastLoadedAt(now);
                row.setErrorMsg(null);
                row.setUpdatedAt(now);
                mapper.insert(row);
                enabledCache.put(cacheKey(t, name), Boolean.TRUE);
            } else {
                existed.setSource(src == null ? existed.getSource() : src.name());
                existed.setLastLoadedAt(now);
                existed.setErrorMsg(null);
                existed.setUpdatedAt(now);
                mapper.updateById(existed);
            }
        }
    }

    @Override
    public synchronized void recordLoadError(String tenantId, String name, SkillSource source, String errorMsg) {
        String t = normalize(tenantId);
        ChatSkillState existed = findOne(t, name);
        LocalDateTime now = LocalDateTime.now();
        if (existed == null) {
            ChatSkillState row = new ChatSkillState();
            row.setTenantId(t);
            row.setName(name);
            row.setEnabled(true);
            row.setSource(source == null ? SkillSource.BUILTIN.name() : source.name());
            row.setErrorMsg(clip(errorMsg));
            row.setUpdatedAt(now);
            mapper.insert(row);
        } else {
            existed.setErrorMsg(clip(errorMsg));
            existed.setUpdatedAt(now);
            mapper.updateById(existed);
        }
    }

    @Override
    public List<ChatSkillState> listAll(String tenantId) {
        String t = normalize(tenantId);
        return mapper.selectList(new QueryWrapper<ChatSkillState>().eq("tenant_id", t).orderByAsc("name"));
    }

    @Override
    public ChatSkillState findOne(String tenantId, String name) {
        String t = normalize(tenantId);
        return mapper.selectOne(new QueryWrapper<ChatSkillState>()
                .eq("tenant_id", t).eq("name", name).last("LIMIT 1"));
    }

    private String cacheKey(String tenantId, String name) {
        return tenantId + "|" + name;
    }

    private String normalize(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

    private String clip(String s) {
        if (s == null) return null;
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
