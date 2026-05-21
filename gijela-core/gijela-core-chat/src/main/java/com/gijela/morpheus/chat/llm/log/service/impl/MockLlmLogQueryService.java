package com.gijela.morpheus.chat.llm.log.service.impl;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.llm.es.enabled", havingValue = "false", matchIfMissing = true)
public class MockLlmLogQueryService implements LlmLogQueryService {

    @Override
    public TimeSeriesResponse timeseries(TimeSeriesRequest request) {
        List<TimeSeriesResponse.TimeSeriesBucket> buckets = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 11; i >= 0; i--) {
            long timestamp = now - i * 300_000L;
            buckets.add(TimeSeriesResponse.TimeSeriesBucket.builder()
                    .timestamp(timestamp)
                    .time(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
                    .value(80D + (12 - i) * 3D)
                    .label(request.getMetric())
                    .build());
        }
        return TimeSeriesResponse.builder()
                .metric(request.getMetric())
                .interval(request.getInterval())
                .buckets(buckets)
                .totalCount((long) buckets.size())
                .queryTimeMs(25L)
                .build();
    }

    @Override
    public TopNResponse topN(TopNRequest request) {
        return TopNResponse.builder()
                .metric(request.getMetric())
                .items(List.of(
                        TopNResponse.Item.builder().traceId("trace-001").sessionId("session-001").modelRoute("gpt-4").value(2450D).extras(Map.of("status", "SUCCESS")).build(),
                        TopNResponse.Item.builder().traceId("trace-002").sessionId("session-002").modelRoute("gpt-4o").value(2180D).extras(Map.of("status", "FAILED")).build()))
                .build();
    }

    @Override
    public DistributionResponse distribution(DistributionRequest request) {
        return DistributionResponse.builder()
                .metric(request.getMetric())
                .buckets(List.of(
                        DistributionResponse.Bucket.builder().label("0-200ms").from(0D).to(200D).count(32L).build(),
                        DistributionResponse.Bucket.builder().label("200-500ms").from(200D).to(500D).count(51L).build(),
                        DistributionResponse.Bucket.builder().label(">500ms").from(500D).to(null).count(9L).build()))
                .build();
    }

    @Override
    public Page<LlmLogRecordVO> records(LogQueryFilter filter, Pageable pageable) {
        List<LlmLogRecordVO> rows = List.of(
                LlmLogRecordVO.builder().traceId("trace-001").sessionId("session-001").modelRoute("gpt-4").status("SUCCESS").latencyMs(480L).totalTokens(1200).eventTime(System.currentTimeMillis()).build(),
                LlmLogRecordVO.builder().traceId("trace-002").sessionId("session-002").modelRoute("gpt-4o").status("FAILED").latencyMs(1310L).totalTokens(880).errorCode("RATE_LIMIT").eventTime(System.currentTimeMillis() - 60_000).build());
        return new PageImpl<>(rows, pageable, rows.size());
    }

    @Override
    public LlmTraceResponse trace(String traceId) {
        List<LlmLogRecordVO> spans = List.of(
                LlmLogRecordVO.builder().traceId(traceId).sessionId("session-001").modelRoute("gpt-4").status("STARTED").latencyMs(0L).eventTime(System.currentTimeMillis() - 5_000).build(),
                LlmLogRecordVO.builder().traceId(traceId).sessionId("session-001").modelRoute("gpt-4").status("SUCCESS").latencyMs(820L).totalTokens(1560).eventTime(System.currentTimeMillis()).build());
        return LlmTraceResponse.builder().traceId(traceId).spans(spans).build();
    }

    @Override
    public ExportLogsResponse export(ExportLogsRequest request) {
        String suffix = request.getFormat() == null ? "csv" : request.getFormat();
        return ExportLogsResponse.builder()
                .downloadUrl("/downloads/llm-logs." + suffix)
                .fileName("llm-logs." + suffix)
                .build();
    }

        @Override
        public LogClearResponse clearLogs(LogQueryFilter filter) {
                return LogClearResponse.builder()
                                .success(true)
                                .deletedCount(0L)
                                .message("当前为 Mock 模式，无可清理日志")
                                .build();
        }
}