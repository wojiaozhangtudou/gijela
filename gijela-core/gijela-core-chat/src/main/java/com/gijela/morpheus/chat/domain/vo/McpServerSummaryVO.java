package com.gijela.morpheus.chat.domain.vo;

import java.time.LocalDateTime;

/**
 * MCP Server 列表项。
 *
 * <p>{@code endpointOrCommand} 用于前端列表统一展示连接目标：</p>
 * <ul>
 *   <li>{@code streamable_http} / {@code sse} → endpoint URL</li>
 *   <li>{@code stdio} → command 字符串（不含 args）</li>
 * </ul>
 */
public record McpServerSummaryVO(
        Long id,
        String name,
        String displayName,
        String description,
        String transport,
        String endpoint,
        String command,
        String endpointOrCommand,
        String authType,
        boolean hasToken,
        boolean enabled,
        String status,
        String statusMessage,
        Integer toolCount,
        LocalDateTime lastTestedAt,
        LocalDateTime updatedAt
) {
}
