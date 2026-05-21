package com.gijela.morpheus.chat.llm.log.service;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LlmLogQueryService {

    TimeSeriesResponse timeseries(TimeSeriesRequest request);

    TopNResponse topN(TopNRequest request);

    DistributionResponse distribution(DistributionRequest request);

    Page<LlmLogRecordVO> records(LogQueryFilter filter, Pageable pageable);

    LlmTraceResponse trace(String traceId);

    ExportLogsResponse export(ExportLogsRequest request);

    LogClearResponse clearLogs(LogQueryFilter filter);
}