package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.llm.sdk.skill.SkillSource;
import com.gijela.morpheus.chat.domain.entity.ChatSkillState;

import java.util.List;
import java.util.Map;

/**
 * 技能运行态服务：管理启停、加载状态记录、错误信息。
 *
 * <p>设计要点：</p>
 * <ul>
 *     <li>启停状态以"启用"为默认值（DB 不存在记录视为启用）；</li>
 *     <li>启停采用内存 cache + DB 写穿，避免每次会话查 DB；</li>
 *     <li>加载记录在 SkillRegistry.reload() 完成后由调度器触发。</li>
 * </ul>
 */
public interface SkillStateService {

    boolean isEnabled(String tenantId, String name);

    void setEnabled(String tenantId, String name, boolean enabled, String operator);

    /**
     * 记录一次成功的加载。currentSources：当前注册表中的全部技能 → source。
     */
    void recordLoadSuccess(String tenantId, Map<String, SkillSource> currentSources);

    /**
     * 记录单个技能加载失败的错误信息。
     */
    void recordLoadError(String tenantId, String name, SkillSource source, String errorMsg);

    /**
     * 列出某租户下所有运行态记录（含未在 DB 落库的技能：返回空表示用默认）。
     */
    List<ChatSkillState> listAll(String tenantId);

    ChatSkillState findOne(String tenantId, String name);
}
