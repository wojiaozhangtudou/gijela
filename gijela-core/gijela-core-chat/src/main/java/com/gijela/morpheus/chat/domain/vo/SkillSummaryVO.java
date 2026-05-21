package com.gijela.morpheus.chat.domain.vo;

import java.time.LocalDateTime;

/**
 * 技能列表项（管理后台）。
 */
public record SkillSummaryVO(
        String name,
        String source,        // BUILTIN / LOCAL
        String version,
        boolean enabled,
        boolean builtin,
        LocalDateTime lastLoadedAt,
        String errorMsg
) {
}
