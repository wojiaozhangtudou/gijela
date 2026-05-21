package com.gijela.morpheus.chat.llm.log.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "TopN 查询响应")
public class TopNResponse {

    @Schema(description = "指标名称")
    private String metric;

    @Schema(description = "结果项")
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String traceId;
        private String sessionId;
        private String modelRoute;
        private Double value;
        private Map<String, Object> extras;
    }
}