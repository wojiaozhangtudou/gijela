package com.gijela.morpheus.chat.domain.vo;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 技能详情（管理后台），在 SkillSummaryVO 基础上额外携带 description / entry / inputSchema / manifestRaw。
 */
public record SkillDetailVO(
        String name,
        String source,
        String version,
        boolean enabled,
        boolean builtin,
        LocalDateTime lastLoadedAt,
        String errorMsg,
        String description,
        String entry,
        Map<String, Object> inputSchema,
        String manifestRaw,
        String sourcePath
) {
}
