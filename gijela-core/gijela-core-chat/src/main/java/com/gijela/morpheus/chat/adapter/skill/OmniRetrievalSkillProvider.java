package com.gijela.morpheus.chat.adapter.skill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gijela.morpheus.chat.adapter.mcp.KnowledgeSearchGateway;
import com.gijela.morpheus.chat.domain.dto.graph.GraphEntityPageRequest;
import com.gijela.morpheus.chat.domain.entity.ChatAttachment;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.graph.GraphEntityItemVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphEntityPageResponse;
import com.gijela.morpheus.chat.mapper.ChatAttachmentMapper;
import com.gijela.morpheus.chat.repository.graph.Neo4jGraphRepository;
import com.gijela.morpheus.llm.sdk.core.tool.ToolCall;
import com.gijela.morpheus.llm.sdk.core.tool.ToolContext;
import com.gijela.morpheus.llm.sdk.core.tool.ToolResult;
import com.gijela.morpheus.llm.sdk.skill.SkillProvider;
import com.gijela.morpheus.llm.sdk.skill.SkillSchemaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 智能多源联合检索技能：根据 query 意图自动路由并聚合附件 / 知识库 / 图谱三路上下文。
 */
public class OmniRetrievalSkillProvider implements SkillProvider {

    private static final Logger log = LoggerFactory.getLogger(OmniRetrievalSkillProvider.class);

    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    // ── 意图关键词 ──────────────────────────────────────────────────────────────
    private static final List<String> ATTACHMENT_KEYWORDS = Arrays.asList(
            "附件", "文件", "上传", "文档里", "这份", "这个文件", "图片"
    );
    private static final List<String> KNOWLEDGE_KEYWORDS = Arrays.asList(
            "怎么配置", "怎么部署", "配置", "部署", "参数", "官方", "规范", "步骤", "如何", "文档"
    );
    private static final List<String> GRAPH_KEYWORDS = Arrays.asList(
            "关系", "图谱", "实体", "谁参与", "连接", "关联", "邻居", "一跳", "路径", "之间"
    );

    private final ChatAttachmentMapper chatAttachmentMapper;
    private final KnowledgeSearchGateway knowledgeSearchGateway;
    private final Neo4jGraphRepository neo4jGraphRepository;

    public OmniRetrievalSkillProvider(ChatAttachmentMapper chatAttachmentMapper,
                                      KnowledgeSearchGateway knowledgeSearchGateway,
                                      Neo4jGraphRepository neo4jGraphRepository) {
        this.chatAttachmentMapper = chatAttachmentMapper;
        this.knowledgeSearchGateway = knowledgeSearchGateway;
        this.neo4jGraphRepository = neo4jGraphRepository;
    }

    @Override
    public String name() {
        return "omni.retrieval";
    }

    @Override
    public Map<String, Object> schema() {
        return SkillSchemaBuilder.functionTool(name(),
                "智能多源联合检索：根据问题自动路由并聚合附件、知识库、图谱上下文，返回统一摘要供 LLM 参考",
                Map.of(
                        "query", Map.of("type", "string", "description", "用户原始问题"),
                        "graphSpace", Map.of("type", "string", "description", "图谱空间，默认 default"),
                        "sources", Map.of("type", "string",
                                "description", "强制指定数据源（逗号分隔）：attachment / knowledge / graph，不传则自动路由"),
                        "topK", Map.of("type", "integer", "description", "每路最多取 K 条，默认 5")
                ),
                List.of("query"));
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        Map<String, Object> args = call.arguments();
        String query = argString(args, "query");
        if (query == null || query.isBlank()) {
            return new ToolResult(call.id(), false, Map.of(), "query 不能为空");
        }
        String tenantId = context == null || context.tenantId() == null || context.tenantId().isBlank()
                ? "default" : context.tenantId();
        String sessionId = extractSessionId(context);
        String graphSpace = argString(args, "graphSpace");
        if (graphSpace == null || graphSpace.isBlank()) graphSpace = "default";
        int topK = argInt(args, "topK", 5, 1, 20);

        // ── 意图路由 ──────────────────────────────────────────────────────────
        String forceSources = argString(args, "sources");
        boolean useAttachment, useKnowledge, useGraph;
        if (forceSources != null && !forceSources.isBlank()) {
            useAttachment = forceSources.contains("attachment");
            useKnowledge  = forceSources.contains("knowledge");
            useGraph      = forceSources.contains("graph");
        } else {
            String qLower = query.toLowerCase();
            useAttachment = ATTACHMENT_KEYWORDS.stream().anyMatch(qLower::contains);
            useKnowledge  = KNOWLEDGE_KEYWORDS.stream().anyMatch(qLower::contains);
            useGraph      = GRAPH_KEYWORDS.stream().anyMatch(qLower::contains);
            // 无明显意图时三路全开
            if (!useAttachment && !useKnowledge && !useGraph) {
                useAttachment = useKnowledge = useGraph = true;
            }
        }

        List<String> activeSources = new ArrayList<>();
        if (useAttachment) activeSources.add("attachment");
        if (useKnowledge)  activeSources.add("knowledge");
        if (useGraph)      activeSources.add("graph");

        // ── 并行拉取 ─────────────────────────────────────────────────────────
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        StringBuilder contextText = new StringBuilder();

        String finalGraphSpace = graphSpace;
        int finalTopK = topK;

        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();

        if (useAttachment && sessionId != null && !sessionId.isBlank()) {
            String finalTenantId = tenantId;
            String finalSessionId = sessionId;
            futures.add(CompletableFuture.supplyAsync(
                    () -> fetchAttachment(finalTenantId, finalSessionId, query, finalTopK), POOL));
        } else {
            if (useAttachment) {
                warnings.add("attachment: sessionId 为空，已跳过");
            }
            futures.add(CompletableFuture.completedFuture(List.of()));
        }

        if (useKnowledge) {
            String finalTenantId = tenantId;
            futures.add(CompletableFuture.supplyAsync(
                    () -> fetchKnowledge(finalTenantId, query), POOL));
        } else {
            futures.add(CompletableFuture.completedFuture(List.of()));
        }

        if (useGraph) {
            String finalTenantId = tenantId;
            futures.add(CompletableFuture.supplyAsync(
                    () -> fetchGraph(finalTenantId, finalGraphSpace, query, finalTopK), POOL));
        } else {
            futures.add(CompletableFuture.completedFuture(List.of()));
        }

        String[] sourceLabels = {"attachment", "knowledge", "graph"};
        for (int i = 0; i < futures.size(); i++) {
            try {
                List<Map<String, Object>> result = futures.get(i).get(5, TimeUnit.SECONDS);
                items.addAll(result);
                if (!result.isEmpty()) {
                    contextText.append(buildContextBlock(sourceLabels[i], result));
                }
            } catch (Exception e) {
                String msg = sourceLabels[i] + ": 拉取超时或失败 - " + e.getMessage();
                warnings.add(msg);
                log.warn("[omni.retrieval] {}", msg);
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("intent", detectIntent(useAttachment, useKnowledge, useGraph));
        output.put("sources", activeSources);
        output.put("items", items);
        output.put("contextText", contextText.toString().trim());
        output.put("warnings", warnings);
        output.put("truncated", false);
        return new ToolResult(call.id(), true, output, null);
    }

    // ── 附件拉取 ────────────────────────────────────────────────────────────────
    private List<Map<String, Object>> fetchAttachment(String tenantId, String sessionId, String query, int topK) {
        try {
            QueryWrapper<ChatAttachment> wrapper = new QueryWrapper<ChatAttachment>()
                    .eq("tenant_id", tenantId)
                    .eq("session_id", sessionId)
                    .eq("process_status", 2)
                    .orderByDesc("id")
                    .last("LIMIT " + topK);
            if (query != null && !query.isBlank()) {
                String q = query.trim();
                wrapper.and(w -> w.like("file_name", q)
                        .or().like("summary", q)
                        .or().like("condensed_md", q));
            }
            List<ChatAttachment> rows = chatAttachmentMapper.selectList(wrapper);
            List<Map<String, Object>> result = new ArrayList<>();
            for (ChatAttachment row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("source", "attachment");
                item.put("score", 0.85);
                item.put("title", row.getFileName());
                item.put("snippet", clip(row.getSummary(), 400) + "\n" + clip(row.getCondensedMd(), 800));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("[omni.retrieval] attachment fetch error", e);
            return List.of();
        }
    }

    // ── 知识库拉取 ──────────────────────────────────────────────────────────────
    private List<Map<String, Object>> fetchKnowledge(String tenantId, String query) {
        try {
            Map<String, Object> raw = knowledgeSearchGateway.search(tenantId, query);
            Object hitsObj = raw.get("hits");
            if (!(hitsObj instanceof List<?> hits)) return List.of();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object h : hits) {
                if (!(h instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> hit = (Map<String, Object>) h;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("source", "knowledge");
                item.put("score", hit.getOrDefault("score", 0.0));
                item.put("chunkId", hit.get("id"));
                item.put("snippet", clip(String.valueOf(hit.getOrDefault("text", "")), 800));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("[omni.retrieval] knowledge fetch error", e);
            return List.of();
        }
    }

    // ── 图谱拉取 ────────────────────────────────────────────────────────────────
    private List<Map<String, Object>> fetchGraph(String tenantId, String graphSpace, String query, int topK) {
        try {
            ChatContext ctx = new ChatContext(tenantId, null, null);
            GraphEntityPageResponse resp = neo4jGraphRepository.pageEntities(
                    ctx,
                    new GraphEntityPageRequest(graphSpace, 1, topK, query, null),
                    graphSpace
            );
            if (resp == null || resp.items() == null) return List.of();
            List<Map<String, Object>> result = new ArrayList<>();
            for (GraphEntityItemVO entity : resp.items()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("source", "graph");
                item.put("score", 0.80);
                item.put("entityName", entity.entityName());
                item.put("entityType", entity.entityType());
                item.put("snippet", entity.entityName() + "（" + entity.entityType() + "）：" + clip(entity.entityDescription(), 300));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("[omni.retrieval] graph fetch error", e);
            return List.of();
        }
    }

    // ── 构建可注入上下文块 ───────────────────────────────────────────────────────
    private String buildContextBlock(String sourceLabel, List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder();
        String header = switch (sourceLabel) {
            case "attachment" -> "【附件摘要】";
            case "knowledge"  -> "【知识库片段】";
            case "graph"      -> "【图谱实体】";
            default           -> "【" + sourceLabel + "】";
        };
        sb.append(header).append("\n");
        for (Map<String, Object> item : items) {
            String snippet = String.valueOf(item.getOrDefault("snippet", ""));
            String title   = String.valueOf(item.getOrDefault("title",
                    item.getOrDefault("entityName", item.getOrDefault("chunkId", ""))));
            sb.append("- ").append(title).append("：").append(snippet).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String detectIntent(boolean att, boolean kn, boolean gr) {
        int count = (att ? 1 : 0) + (kn ? 1 : 0) + (gr ? 1 : 0);
        if (count >= 2) return "MIXED";
        if (att) return "ATTACHMENT";
        if (kn)  return "KNOWLEDGE";
        if (gr)  return "GRAPH";
        return "UNKNOWN";
    }

    // ── 工具方法 ─────────────────────────────────────────────────────────────────
    private String extractSessionId(ToolContext context) {
        if (context == null || context.attributes() == null) return null;
        Object v = context.attributes().get("sessionId");
        return v == null ? null : String.valueOf(v);
    }

    private String argString(Map<String, Object> args, String key) {
        if (args == null || key == null) return null;
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private int argInt(Map<String, Object> args, String key, int def, int min, int max) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v == null) return def;
        try {
            int parsed = Integer.parseInt(String.valueOf(v));
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String clip(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}
