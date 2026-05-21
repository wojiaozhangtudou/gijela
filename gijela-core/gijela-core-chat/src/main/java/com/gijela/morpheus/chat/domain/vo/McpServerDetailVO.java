package com.gijela.morpheus.chat.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 详情。
 */
public record McpServerDetailVO(
        Long id,
        String name,
        String displayName,
        String description,
        String transport,
        String endpoint,
        String command,
        List<String> args,
        Map<String, String> env,
        String workingDir,
        String authType,
        /** token 掩码：null（无密钥）或形如 "***abcd" */
        String authTokenMask,
        boolean enabled,
        String status,
        String statusMessage,
        List<Map<String, Object>> tools,
        LocalDateTime lastTestedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
