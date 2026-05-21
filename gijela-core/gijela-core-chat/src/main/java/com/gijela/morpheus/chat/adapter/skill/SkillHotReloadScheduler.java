package com.gijela.morpheus.chat.adapter.skill;

import com.gijela.morpheus.chat.service.SkillStateService;
import com.gijela.morpheus.llm.sdk.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 定时热加载：每 30 秒重新扫描本地技能目录，自动发现新增/移除的技能包。
 *
 * <p>每次 reload 完成后会同步把当前技能集合写入 chat_skill_state（last_loaded_at）。</p>
 */
@Component
public class SkillHotReloadScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SkillHotReloadScheduler.class);

    private final SkillRegistry skillRegistry;
    private final SkillStateService skillStateService;

    public SkillHotReloadScheduler(SkillRegistry skillRegistry, SkillStateService skillStateService) {
        this.skillRegistry = skillRegistry;
        this.skillStateService = skillStateService;
    }

    @PostConstruct
    public void onStartup() {
        // 启动时把当前内置 + 本地技能登记一次 last_loaded_at
        try {
            skillStateService.recordLoadSuccess("default", skillRegistry.sourcesSnapshot());
        } catch (Exception e) {
            logger.warn("[skills] startup recordLoadSuccess failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void scheduledReload() {
        try {
            reloadAndRecord();
        } catch (Exception e) {
            logger.warn("[skills] hot-reload error: {}", e.getMessage());
        }
    }

    /** 供管理后台 reload 接口手动触发。 */
    public synchronized void reloadAndRecord() {
        skillRegistry.reload();
        skillStateService.recordLoadSuccess("default", skillRegistry.sourcesSnapshot());
    }
}
