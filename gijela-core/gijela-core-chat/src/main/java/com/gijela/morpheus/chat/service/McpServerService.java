package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.chat.domain.dto.McpServerSaveRequest;
import com.gijela.morpheus.chat.domain.vo.McpServerDetailVO;
import com.gijela.morpheus.chat.domain.vo.McpServerSummaryVO;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 业务管理服务。
 *
 * <p>与 SDK 相关的 {@code loadActiveBindings} 已下沉到
 * {@code McpToolBindingSource} 接口，由 {@code MyBatisMcpServerService} 同时实现两者。</p>
 */
public interface McpServerService {

    List<McpServerSummaryVO> list(String tenantId);

    McpServerDetailVO detail(String tenantId, String name);

    McpServerDetailVO create(String tenantId, McpServerSaveRequest request, String operator);

    McpServerDetailVO update(String tenantId, String name, McpServerSaveRequest request, String operator);

    void delete(String tenantId, String name);

    McpServerDetailVO toggle(String tenantId, String name, boolean enabled, String operator);

    /** 连通性测试：执行 initialize；返回 {ok, message, serverInfo, capabilities}。 */
    Map<String, Object> test(String tenantId, String name);

    /** 重新拉取 tools/list 并刷新缓存。 */
    McpServerDetailVO refreshTools(String tenantId, String name);
}
