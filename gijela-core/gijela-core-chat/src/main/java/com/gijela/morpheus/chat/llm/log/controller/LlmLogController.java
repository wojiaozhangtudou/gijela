package com.gijela.morpheus.chat.llm.log.controller;

import com.gijela.morpheus.chat.llm.log.dto.request.DistributionRequest;
import com.gijela.morpheus.chat.llm.log.dto.request.ExportLogsRequest;
import com.gijela.morpheus.chat.llm.log.dto.request.LogQueryFilter;
import com.gijela.morpheus.chat.llm.log.dto.request.TimeSeriesRequest;
import com.gijela.morpheus.chat.llm.log.dto.request.TopNRequest;
import com.gijela.morpheus.chat.llm.log.dto.response.DistributionResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.ExportLogsResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.LogClearResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.LlmLogRecordVO;
import com.gijela.morpheus.chat.llm.log.dto.response.LlmTraceResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.TimeSeriesResponse;
import com.gijela.morpheus.chat.llm.log.dto.response.TopNResponse;
import com.gijela.morpheus.chat.llm.log.service.LlmLogQueryService;
import com.gijela.morpheus.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * LLM 日志查询 API
 * 提供 6 个查询端点用于前端看板展示
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/llm-logs")
@Tag(name = "LLM 日志查询", description = "LLM 日志查询和聚合接口")
@SecurityRequirement(name = "Bearer Authentication")
public class LlmLogController {

    private final LlmLogQueryService llmLogQueryService;

    public LlmLogController(LlmLogQueryService llmLogQueryService) {
        this.llmLogQueryService = llmLogQueryService;
    }
    
    /**
     * 时间序列聚合查询
     * 用途：前端折线图、趋势图
     * 
     * 示例请求：
     * POST /api/v1/llm-logs/query/timeseries
     * {
     *   "filter": {
     *     "startAt": "2026-05-07 10:00:00",
     *     "endAt": "2026-05-07 11:00:00",
     *     "modelRoute": "gpt-4"
     *   },
     *   "metric": "qps",
     *   "interval": "5m"
     * }
     */
    @PostMapping("/query/timeseries")
    @Operation(summary = "时间序列查询", description = "按时间聚合查询指标（QPS、错误率、延迟等）")
    public ApiResponse<TimeSeriesResponse> timeseries(
            @RequestBody TimeSeriesRequest request) {
        log.debug("Querying timeseries: metric={}, interval={}", 
            request.getMetric(), request.getInterval());

        return ApiResponse.ok(llmLogQueryService.timeseries(request));
    }
    
    /**
     * Top N 查询
     * 用途：前端排序表（最慢请求、高成本请求等）
     */
    @PostMapping("/query/topn")
    @Operation(summary = "Top N 查询", description = "按指定指标排序取 Top N")
    public ApiResponse<TopNResponse> topN(
            @RequestBody TopNRequest request) {

        log.debug("Querying topN: metric={}, limit={}", request.getMetric(), request.getLimit());

        return ApiResponse.ok(llmLogQueryService.topN(request));
    }
    
    /**
     * 分布查询
     * 用途：前端直方图（延迟分布、token 分布等）
     */
    @PostMapping("/query/distribution")
    @Operation(summary = "分布查询", description = "按指定指标统计分布（直方图）")
    public ApiResponse<DistributionResponse> distribution(
            @RequestBody DistributionRequest request) {

        log.debug("Querying distribution: metric={}, buckets={}", request.getMetric(), request.getBuckets());

        return ApiResponse.ok(llmLogQueryService.distribution(request));
    }
    
    /**
     * 记录查询
     * 用途：前端日志表展示详细记录
     */
    @GetMapping("/query/records")
    @Operation(summary = "记录查询", description = "按条件查询日志记录（分页）")
    public ApiResponse<Page<LlmLogRecordVO>> records(
            LogQueryFilter filter,
            Pageable pageable) {

        log.debug("Querying records: filter={}, page={}", filter, pageable.getPageNumber());

        return ApiResponse.ok(llmLogQueryService.records(filter, pageable));
    }
    
    /**
     * 链路追踪查询
     * 用途：前端链路详情页面
     */
    @GetMapping("/query/trace/{traceId}")
    @Operation(summary = "链路追踪", description = "按 traceId 查询完整链路信息")
    public ApiResponse<LlmTraceResponse> trace(
            @PathVariable String traceId) {

        log.debug("Querying trace: traceId={}", traceId);

        return ApiResponse.ok(llmLogQueryService.trace(traceId));
    }
    
    /**
     * 日志导出
     * 用途：前端下载 CSV/Excel
     */
    @PostMapping("/export")
    @Operation(summary = "日志导出", description = "导出查询结果为 CSV")
    public ApiResponse<ExportLogsResponse> export(
            @RequestBody ExportLogsRequest request) {

        log.debug("Exporting logs");

        return ApiResponse.ok(llmLogQueryService.export(request));
    }

    /**
     * 日志清理
     * 用途：运维一键清理日志数据（支持按过滤条件清理）
     */
    @PostMapping("/maintenance/clear")
    @Operation(summary = "清理日志", description = "清理日志数据（默认全量，可按过滤条件）")
    public ApiResponse<LogClearResponse> clearLogs(
            @RequestBody(required = false) LogQueryFilter filter) {

        log.warn("Clearing logs by filter: {}", filter);

        return ApiResponse.ok(llmLogQueryService.clearLogs(filter));
    }
}
