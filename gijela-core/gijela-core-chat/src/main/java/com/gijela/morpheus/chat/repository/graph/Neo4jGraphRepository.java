package com.gijela.morpheus.chat.repository.graph;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.dto.graph.GraphRelationshipPageRequest;
import com.gijela.morpheus.chat.domain.dto.graph.GraphEntityPageRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.graph.GraphEntityItemVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphEntityPageResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphEntityTypeListResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractEntityVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractPreviewResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractRelationshipVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphMutationResultResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphOneHopQueryResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphRelationshipItemVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphRelationshipPageResponse;
import com.gijela.morpheus.chat.domain.vo.graph.GraphSpaceItemVO;
import com.gijela.morpheus.chat.domain.vo.graph.GraphSpaceListResponse;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Neo4jGraphRepository {

    private static final Logger log = LoggerFactory.getLogger(Neo4jGraphRepository.class);

    private final ObjectProvider<Driver> neo4jDriverProvider;
    private final ChatModuleProperties properties;

    public Neo4jGraphRepository(ObjectProvider<Driver> neo4jDriverProvider,
                                ChatModuleProperties properties) {
        this.neo4jDriverProvider = neo4jDriverProvider;
        this.properties = properties;
    }

    public void ensureSchema() {
        Driver driver = requireDriver();
        try (Session session = openSession(driver)) {
            session.run("CREATE CONSTRAINT entity_identity IF NOT EXISTS FOR (n:Entity) REQUIRE (n.tenantId, n.graphSpace, n.normalizedName) IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT graph_space_identity IF NOT EXISTS FOR (n:GraphSpaceMeta) REQUIRE (n.tenantId, n.graphSpace) IS UNIQUE").consume();
            session.run("CREATE INDEX entity_updated_at IF NOT EXISTS FOR (n:Entity) ON (n.updatedAt)").consume();
        } catch (Exception ex) {
            log.error("初始化 Neo4j 图谱约束失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "初始化图谱约束失败");
        }
    }

    public void importPreview(ChatContext context,
                              GraphExtractPreviewResponse preview,
                              String importBatchId,
                              String importMode) {
        Driver driver = requireDriver();
        ensureSchema();
        try (Session session = openSession(driver)) {
            session.executeWrite(tx -> {
                ensureSpaceMeta(tx, context, preview.graphSpace());
                if ("REPLACE_SPACE".equalsIgnoreCase(importMode)) {
                    tx.run("MATCH (n {tenantId:$tenantId, graphSpace:$graphSpace}) WHERE n:Entity DETACH DELETE n",
                            Values.parameters("tenantId", context.tenantId(), "graphSpace", preview.graphSpace()));
                }
                for (GraphExtractEntityVO entity : preview.entities()) {
                    mergeEntity(tx, context, preview.graphSpace(), importBatchId, entity);
                }
                for (GraphExtractRelationshipVO relationship : preview.relationships()) {
                    mergeRelationship(tx, context, preview.graphSpace(), importBatchId, relationship);
                }
                return null;
            });
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("导入图谱预览失败", ex);
            throw new BizException(ErrorCode.GRAPH_IMPORT_CONSTRAINT_CONFLICT, "图谱导入失败");
        }
    }

    public GraphSpaceListResponse listSpaces(ChatContext context) {
        Driver driver = requireDriver();
        ensureSchema();
        try (Session session = openSession(driver)) {
            Result result = session.run("MATCH (s:GraphSpaceMeta {tenantId:$tenantId}) "
                    + "OPTIONAL MATCH (n:Entity {tenantId:$tenantId, graphSpace:s.graphSpace}) "
                    + "OPTIONAL MATCH ()-[r:RELATED_TO {tenantId:$tenantId, graphSpace:s.graphSpace}]->() "
                    + "RETURN s.graphSpace AS graphSpace, count(DISTINCT n) AS entityCount, count(DISTINCT r) AS relationshipCount "
                    + "ORDER BY s.graphSpace ASC",
                    Values.parameters("tenantId", context.tenantId()));
            List<GraphSpaceItemVO> items = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                items.add(new GraphSpaceItemVO(
                        record.get("graphSpace").asString(""),
                        record.get("entityCount").asLong(0L),
                        record.get("relationshipCount").asLong(0L)
                ));
            }
            if (items.stream().noneMatch(item -> properties.getGraph().getDefaultSpace().equals(item.graphSpace()))) {
                items.add(0, new GraphSpaceItemVO(properties.getGraph().getDefaultSpace(), 0L, 0L));
            }
            return new GraphSpaceListResponse(properties.getGraph().getDefaultSpace(), items.size(), items);
        } catch (Exception ex) {
            log.error("查询图谱空间失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "查询图谱空间失败");
        }
    }

    public GraphSpaceItemVO createSpace(ChatContext context, String graphSpace) {
        Driver driver = requireDriver();
        ensureSchema();
        try (Session session = openSession(driver)) {
            session.executeWrite(tx -> {
                ensureSpaceMeta(tx, context, graphSpace);
                return null;
            });
            return new GraphSpaceItemVO(graphSpace, 0L, 0L);
        } catch (Exception ex) {
            log.error("创建图谱空间失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "创建图谱空间失败");
        }
    }

    public GraphMutationResultResponse deleteSpace(ChatContext context, String graphSpace) {
        Driver driver = requireDriver();
        try (Session session = openSession(driver)) {
            int affected = session.executeWrite(tx -> {
                Result deleteRelationships = tx.run("MATCH ()-[r:RELATED_TO {tenantId:$tenantId, graphSpace:$graphSpace}]->() DELETE r RETURN count(r) AS affected",
                        Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace));
                long relCount = deleteRelationships.single().get("affected").asLong(0L);
                Result deleteEntities = tx.run("MATCH (n:Entity {tenantId:$tenantId, graphSpace:$graphSpace}) DETACH DELETE n RETURN count(n) AS affected",
                        Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace));
                long entityCount = deleteEntities.single().get("affected").asLong(0L);
                tx.run("MATCH (s:GraphSpaceMeta {tenantId:$tenantId, graphSpace:$graphSpace}) DELETE s",
                        Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace));
                return (int) (relCount + entityCount);
            });
            return new GraphMutationResultResponse(graphSpace, "DELETE_SPACE", affected);
        } catch (Exception ex) {
            log.error("删除图谱空间失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "删除图谱空间失败");
        }
    }

    public GraphEntityPageResponse pageEntities(ChatContext context, GraphEntityPageRequest request, String graphSpace) {
        Driver driver = requireDriver();
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 20 : Math.min(request.pageSize(), 100);
        long skip = (long) (page - 1) * pageSize;
        try (Session session = openSession(driver)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("tenantId", context.tenantId());
            params.put("graphSpace", graphSpace);
            params.put("skip", skip);
            params.put("limit", pageSize);
            params.put("keyword", request.keyword() == null ? "" : request.keyword().trim());
                params.put("entityType", request.entityType() == null ? "" : request.entityType().trim());
            String whereClause = "WHERE n.tenantId = $tenantId AND n.graphSpace = $graphSpace "
                    + "AND ($keyword = '' OR n.entityName CONTAINS $keyword OR n.entityDescription CONTAINS $keyword) "
                    + "AND ($entityType = '' OR n.entityType = $entityType) ";
            Result countResult = session.run("MATCH (n:Entity) " + whereClause + "RETURN count(n) AS total", params);
            long total = countResult.single().get("total").asLong();
            Result result = session.run("MATCH (n:Entity) " + whereClause
                    + "RETURN n.entityName AS entityName, n.normalizedName AS normalizedName, n.entityType AS entityType, "
                    + "n.entityDescription AS entityDescription, n.graphSpace AS graphSpace, toString(n.updatedAt) AS updatedAt "
                    + "ORDER BY n.updatedAt DESC SKIP $skip LIMIT $limit", params);
            List<GraphEntityItemVO> items = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                items.add(new GraphEntityItemVO(
                        record.get("entityName").asString(""),
                        record.get("normalizedName").asString(""),
                        record.get("entityType").asString(""),
                        record.get("entityDescription").asString(""),
                        record.get("graphSpace").asString(""),
                        record.get("updatedAt").asString("")
                ));
            }
            return new GraphEntityPageResponse(graphSpace, page, pageSize, total, items);
        } catch (Exception ex) {
            log.error("分页查询图实体失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "分页查询图实体失败");
        }
    }

    public GraphEntityTypeListResponse listEntityTypes(ChatContext context, String graphSpace) {
        Driver driver = requireDriver();
        try (Session session = openSession(driver)) {
            Result result = session.run("MATCH (n:Entity) "
                            + "WHERE n.tenantId = $tenantId AND n.graphSpace = $graphSpace "
                            + "AND n.entityType IS NOT NULL AND trim(n.entityType) <> '' "
                            + "RETURN DISTINCT trim(n.entityType) AS entityType ORDER BY entityType ASC",
                    Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace));

            List<String> items = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                String entityType = record.get("entityType").asString("").trim();
                if (!entityType.isBlank()) {
                    items.add(entityType);
                }
            }
            return new GraphEntityTypeListResponse(graphSpace, items.size(), items);
        } catch (Exception ex) {
            log.error("查询实体类型列表失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "查询实体类型列表失败");
        }
    }

    public GraphRelationshipPageResponse pageRelationships(ChatContext context, GraphRelationshipPageRequest request, String graphSpace) {
        Driver driver = requireDriver();
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 20 : Math.min(request.pageSize(), 100);
        long skip = (long) (page - 1) * pageSize;
        try (Session session = openSession(driver)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("tenantId", context.tenantId());
            params.put("graphSpace", graphSpace);
            params.put("skip", skip);
            params.put("limit", pageSize);
            params.put("keyword", request.keyword() == null ? "" : request.keyword().trim());
                params.put("entityType", request.entityType() == null ? "" : request.entityType().trim());
            String whereClause = "WHERE r.tenantId = $tenantId AND r.graphSpace = $graphSpace "
                    + "AND ($keyword = '' OR a.entityName CONTAINS $keyword OR b.entityName CONTAINS $keyword OR r.description CONTAINS $keyword) "
                    + "AND ($entityType = '' OR a.entityType = $entityType OR b.entityType = $entityType) ";
            Result countResult = session.run("MATCH (a:Entity)-[r:RELATED_TO]->(b:Entity) " + whereClause + "RETURN count(r) AS total", params);
            long total = countResult.single().get("total").asLong();
            Result result = session.run("MATCH (a:Entity)-[r:RELATED_TO]->(b:Entity) " + whereClause
                    + "RETURN r.relationshipId AS relationshipId, a.entityName AS sourceEntity, b.entityName AS targetEntity, "
                    + "r.description AS relationshipDescription, r.strength AS relationshipStrength, r.graphSpace AS graphSpace, toString(r.updatedAt) AS updatedAt "
                    + "ORDER BY r.updatedAt DESC SKIP $skip LIMIT $limit", params);
            List<GraphRelationshipItemVO> items = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                items.add(new GraphRelationshipItemVO(
                        record.get("relationshipId").asString(""),
                        record.get("sourceEntity").asString(""),
                        record.get("targetEntity").asString(""),
                        record.get("relationshipDescription").asString(""),
                        record.get("relationshipStrength").isNull() ? null : record.get("relationshipStrength").asInt(),
                        record.get("graphSpace").asString(""),
                        record.get("updatedAt").asString("")
                ));
            }
            return new GraphRelationshipPageResponse(graphSpace, page, pageSize, total, items);
        } catch (Exception ex) {
            log.error("分页查询图关系失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "分页查询图关系失败");
        }
    }

        public GraphOneHopQueryResponse queryOneHop(ChatContext context,
                            String graphSpace,
                            String centerEntity,
                            Integer limitNodes,
                            Integer limitEdges) {
        Driver driver = requireDriver();
        int edgeLimit = limitEdges == null || limitEdges < 1 ? 200 : Math.min(limitEdges, 1000);
        int nodeLimit = limitNodes == null || limitNodes < 1 ? 100 : Math.min(limitNodes, 500);
        try (Session session = openSession(driver)) {
            Result centerResult = session.run("MATCH (n:Entity {tenantId:$tenantId, graphSpace:$graphSpace}) "
                    + "WHERE n.entityName = $centerEntity OR n.normalizedName = $centerEntity "
                    + "RETURN n.normalizedName AS normalizedName, n.entityName AS entityName, n.entityType AS entityType, "
                    + "n.entityDescription AS entityDescription, n.graphSpace AS graphSpace, toString(n.updatedAt) AS updatedAt "
                    + "LIMIT 1",
                Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace, "centerEntity", centerEntity));
            if (!centerResult.hasNext()) {
            return new GraphOneHopQueryResponse(graphSpace, centerEntity, 0, 0, List.of(), List.of());
            }

            Record centerRecord = centerResult.single();
            String centerNormalized = centerRecord.get("normalizedName").asString(centerEntity);
            GraphEntityItemVO centerNode = new GraphEntityItemVO(
                centerRecord.get("entityName").asString(""),
                centerRecord.get("normalizedName").asString(""),
                centerRecord.get("entityType").asString(""),
                centerRecord.get("entityDescription").asString(""),
                centerRecord.get("graphSpace").asString(graphSpace),
                centerRecord.get("updatedAt").asString("")
            );

            Result relationshipResult = session.run("MATCH (a:Entity)-[r:RELATED_TO]->(b:Entity) "
                    + "WHERE r.tenantId = $tenantId AND r.graphSpace = $graphSpace "
                    + "AND (a.normalizedName = $centerNormalized OR b.normalizedName = $centerNormalized) "
                    + "RETURN r.relationshipId AS relationshipId, a.entityName AS sourceEntity, b.entityName AS targetEntity, "
                    + "r.description AS relationshipDescription, r.strength AS relationshipStrength, r.graphSpace AS graphSpace, toString(r.updatedAt) AS updatedAt "
                    + "ORDER BY r.updatedAt DESC LIMIT $edgeLimit",
                Values.parameters(
                    "tenantId", context.tenantId(),
                    "graphSpace", graphSpace,
                    "centerNormalized", centerNormalized,
                    "edgeLimit", edgeLimit
                ));
            List<GraphRelationshipItemVO> edges = new ArrayList<>();
            Set<String> entityNames = new HashSet<>();
            entityNames.add(centerNode.entityName());
            while (relationshipResult.hasNext()) {
            Record record = relationshipResult.next();
            GraphRelationshipItemVO edge = new GraphRelationshipItemVO(
                record.get("relationshipId").asString(""),
                record.get("sourceEntity").asString(""),
                record.get("targetEntity").asString(""),
                record.get("relationshipDescription").asString(""),
                record.get("relationshipStrength").isNull() ? null : record.get("relationshipStrength").asInt(),
                record.get("graphSpace").asString(graphSpace),
                record.get("updatedAt").asString("")
            );
            edges.add(edge);
            entityNames.add(edge.sourceEntity());
            entityNames.add(edge.targetEntity());
            }

            Result entityResult = session.run("MATCH (n:Entity {tenantId:$tenantId, graphSpace:$graphSpace}) "
                    + "WHERE n.entityName IN $entityNames "
                    + "RETURN n.entityName AS entityName, n.normalizedName AS normalizedName, n.entityType AS entityType, "
                    + "n.entityDescription AS entityDescription, n.graphSpace AS graphSpace, toString(n.updatedAt) AS updatedAt "
                    + "ORDER BY n.updatedAt DESC LIMIT $nodeLimit",
                Values.parameters(
                    "tenantId", context.tenantId(),
                    "graphSpace", graphSpace,
                    "entityNames", new ArrayList<>(entityNames),
                    "nodeLimit", nodeLimit
                ));
            List<GraphEntityItemVO> nodes = new ArrayList<>();
            while (entityResult.hasNext()) {
            Record record = entityResult.next();
            nodes.add(new GraphEntityItemVO(
                record.get("entityName").asString(""),
                record.get("normalizedName").asString(""),
                record.get("entityType").asString(""),
                record.get("entityDescription").asString(""),
                record.get("graphSpace").asString(graphSpace),
                record.get("updatedAt").asString("")
            ));
            }
            if (nodes.stream().noneMatch(item -> item.normalizedName().equals(centerNode.normalizedName()))) {
            nodes.add(0, centerNode);
            }
            return new GraphOneHopQueryResponse(graphSpace, centerEntity, nodes.size(), edges.size(), nodes, edges);
        } catch (Exception ex) {
            log.error("查询一跳邻居失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "查询一跳邻居失败");
        }
        }

    public GraphMutationResultResponse deleteEntities(ChatContext context, String graphSpace, List<String> normalizedNames) {
        Driver driver = requireDriver();
        try (Session session = openSession(driver)) {
            int affected = session.executeWrite(tx -> {
                Result result = tx.run("MATCH (n:Entity {tenantId:$tenantId, graphSpace:$graphSpace}) WHERE n.normalizedName IN $normalizedNames DETACH DELETE n RETURN count(n) AS affected",
                        Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace, "normalizedNames", normalizedNames));
                return (int) result.single().get("affected").asLong(0L);
            });
            return new GraphMutationResultResponse(graphSpace, "DELETE_ENTITIES", affected);
        } catch (Exception ex) {
            log.error("删除图实体失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "删除图实体失败");
        }
    }

    public GraphMutationResultResponse deleteRelationships(ChatContext context, String graphSpace, List<String> relationshipIds) {
        Driver driver = requireDriver();
        try (Session session = openSession(driver)) {
            int affected = session.executeWrite(tx -> {
                Result result = tx.run("MATCH ()-[r:RELATED_TO {tenantId:$tenantId, graphSpace:$graphSpace}]->() WHERE r.relationshipId IN $relationshipIds DELETE r RETURN count(r) AS affected",
                        Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace, "relationshipIds", relationshipIds));
                return (int) result.single().get("affected").asLong(0L);
            });
            return new GraphMutationResultResponse(graphSpace, "DELETE_RELATIONSHIPS", affected);
        } catch (Exception ex) {
            log.error("删除图关系失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "删除图关系失败");
        }
    }

    public GraphMutationResultResponse deleteImportBatch(ChatContext context, String graphSpace, String importBatchId) {
        Driver driver = requireDriver();
        try (Session session = openSession(driver)) {
            int affected = session.executeWrite(tx -> {
                Result deleteRelationships = tx.run("MATCH ()-[r:RELATED_TO {tenantId:$tenantId, graphSpace:$graphSpace, importBatchId:$importBatchId}]->() DELETE r RETURN count(r) AS affected",
                        Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace, "importBatchId", importBatchId));
                long relationshipCount = deleteRelationships.single().get("affected").asLong(0L);
                Result deleteEntities = tx.run("MATCH (n:Entity {tenantId:$tenantId, graphSpace:$graphSpace, importBatchId:$importBatchId}) DETACH DELETE n RETURN count(n) AS affected",
                        Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace, "importBatchId", importBatchId));
                long entityCount = deleteEntities.single().get("affected").asLong(0L);
                return (int) (relationshipCount + entityCount);
            });
            return new GraphMutationResultResponse(graphSpace, "DELETE_IMPORT_BATCH", affected);
        } catch (Exception ex) {
            log.error("按批次删除图谱数据失败", ex);
            throw new BizException(ErrorCode.DB_ERROR, "按批次删除图谱数据失败");
        }
    }

    private void mergeEntity(TransactionContext tx,
                             ChatContext context,
                             String graphSpace,
                             String importBatchId,
                             GraphExtractEntityVO entity) {
        tx.run("MERGE (n:Entity {tenantId:$tenantId, graphSpace:$graphSpace, normalizedName:$normalizedName}) "
                        + "SET n.entityName = $entityName, n.entityType = $entityType, n.entityDescription = $entityDescription, "
                        + "n.importBatchId = $importBatchId, n.updatedAt = datetime(), n.createdAt = coalesce(n.createdAt, datetime())",
                Values.parameters(
                        "tenantId", context.tenantId(),
                        "graphSpace", graphSpace,
                        "normalizedName", entity.normalizedName(),
                        "entityName", entity.entityName(),
                        "entityType", entity.entityType(),
                        "entityDescription", entity.entityDescription(),
                        "importBatchId", importBatchId
                ));
    }

    private void ensureSpaceMeta(TransactionContext tx, ChatContext context, String graphSpace) {
        tx.run("MERGE (s:GraphSpaceMeta {tenantId:$tenantId, graphSpace:$graphSpace}) "
                        + "SET s.updatedAt = datetime(), s.createdAt = coalesce(s.createdAt, datetime())",
                Values.parameters("tenantId", context.tenantId(), "graphSpace", graphSpace));
    }

    private void mergeRelationship(TransactionContext tx,
                                   ChatContext context,
                                   String graphSpace,
                                   String importBatchId,
                                   GraphExtractRelationshipVO relationship) {
        tx.run("MATCH (a:Entity {tenantId:$tenantId, graphSpace:$graphSpace, normalizedName:$source}) "
                        + "MATCH (b:Entity {tenantId:$tenantId, graphSpace:$graphSpace, normalizedName:$target}) "
                        + "MERGE (a)-[r:RELATED_TO {tenantId:$tenantId, graphSpace:$graphSpace, relationshipId:$relationshipId}]->(b) "
                        + "SET r.description = $description, r.strength = $strength, r.importBatchId = $importBatchId, "
                        + "r.updatedAt = datetime(), r.createdAt = coalesce(r.createdAt, datetime())",
                Values.parameters(
                        "tenantId", context.tenantId(),
                        "graphSpace", graphSpace,
                        "source", relationship.sourceEntity(),
                        "target", relationship.targetEntity(),
                        "relationshipId", relationship.relationshipId(),
                        "description", relationship.relationshipDescription(),
                        "strength", relationship.relationshipStrength(),
                        "importBatchId", importBatchId
                ));
    }

    private Driver requireDriver() {
        if (!properties.getGraph().isEnabled()) {
            throw new BizException(ErrorCode.GRAPH_MODULE_DISABLED, "图谱模块未启用");
        }
        Driver driver = neo4jDriverProvider.getIfAvailable();
        if (driver == null) {
            throw new BizException(ErrorCode.GRAPH_MODULE_DISABLED, "图谱连接未配置");
        }
        return driver;
    }

    private Session openSession(Driver driver) {
        String database = properties.getGraph().getDatabase();
        if (database == null || database.isBlank()) {
            return driver.session();
        }
        return driver.session(SessionConfig.forDatabase(database));
    }
}