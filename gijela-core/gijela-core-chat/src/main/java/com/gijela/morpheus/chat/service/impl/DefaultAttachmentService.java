package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.adapter.extractor.FileContentExtractor;
import com.gijela.morpheus.chat.adapter.storage.ObjectStorageGateway;
import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.entity.ChatAttachment;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.mapper.ChatAttachmentMapper;
import com.gijela.morpheus.chat.service.AttachmentService;
import com.gijela.morpheus.chat.service.AttachmentSummaryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultAttachmentService implements AttachmentService {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectStorageGateway objectStorageGateway;
    private final ChatAttachmentMapper chatAttachmentMapper;
    private final FileContentExtractor fileContentExtractor;
    private final AttachmentSummaryService attachmentSummaryService;
    private final ChatModuleProperties properties;

    public DefaultAttachmentService(ObjectStorageGateway objectStorageGateway,
                                    ChatAttachmentMapper chatAttachmentMapper,
                                    FileContentExtractor fileContentExtractor,
                                    AttachmentSummaryService attachmentSummaryService,
                                    ChatModuleProperties properties) {
        this.objectStorageGateway = objectStorageGateway;
        this.chatAttachmentMapper = chatAttachmentMapper;
        this.fileContentExtractor = fileContentExtractor;
        this.attachmentSummaryService = attachmentSummaryService;
        this.properties = properties;
    }

    @Override
    public Map<String, Object> upload(ChatContext context, MultipartFile file, String summaryModel) {
        if (context.sessionId() == null || context.sessionId().isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "上传附件必须携带有效的 sessionId");
        }

        // 提前读取文件字节（用于内容提取，避免流关闭后无法再读）
        byte[] fileBytes = null;
        if (file != null && !file.isEmpty()) {
            try {
                fileBytes = file.getBytes();
            } catch (IOException e) {
                throw new RuntimeException("读取上传文件失败", e);
            }
        }

        Map<String, Object> result;
        try {
            result = objectStorageGateway.upload(context, file);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            String detail = ex.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = ex.getClass().getSimpleName();
            }
            throw new BizException(ErrorCode.NETWORK_IO_ERROR,
                    "附件上传失败：对象存储调用异常，请检查 CHAT_STORAGE_MODE、CHAT_STORAGE_ENDPOINT、CHAT_STORAGE_BUCKET 以及认证配置。原因："
                            + detail
            );
        }

        // 提取原始文本（txt/md），不支持的类型为 null
        String rawText = null;
        if (fileBytes != null) {
            int maxChars = properties.getAttachmentSummary().getMaxRawChars();
            rawText = fileContentExtractor.extract(file.getOriginalFilename(), file.getContentType(), fileBytes, maxChars);
        }

        ChatAttachment entity = new ChatAttachment();
        entity.setTenantId(context.tenantId() == null || context.tenantId().isBlank() ? "default" : context.tenantId());
        String sessionId = context.sessionId();
        entity.setSessionId(sessionId == null || sessionId.isBlank() ? null : sessionId);
        entity.setFileName(String.valueOf(result.getOrDefault("fileName", file == null ? "" : file.getOriginalFilename())));
        entity.setObjectKey(String.valueOf(result.getOrDefault("objectKey", "")));
        entity.setFileSize((Long) result.getOrDefault("size", file == null ? 0L : file.getSize()));
        entity.setContentType(file == null ? null : file.getContentType());
        entity.setProvider(String.valueOf(result.getOrDefault("provider", "rustfs-mock")));
        entity.setRawText(rawText);
        entity.setProcessStatus(0); // 待处理
        entity.setCreatedAt(LocalDateTime.now());
        chatAttachmentMapper.insert(entity);

        // 异步触发摘要提炼
        attachmentSummaryService.processAsync(entity.getId(), summaryModel);

        Map<String, Object> response = new LinkedHashMap<>(result);
        response.put("attachmentId", entity.getId());
        return response;
    }

    @Override
    public Map<String, Object> getStatus(Long attachmentId) {
        return attachmentSummaryService.getStatus(attachmentId);
    }

    @Override
    public Map<String, Object> listBySession(ChatContext context,
                                             String sessionId,
                                             Integer page,
                                             Integer pageSize,
                                             Integer status,
                                             String fileNameLike,
                                             String startTime,
                                             String endTime) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "sessionId 不能为空");
        }
        int normalizedPage = (page == null || page <= 0) ? 1 : page;
        int normalizedPageSize = (pageSize == null || pageSize <= 0) ? 20 : Math.min(pageSize, 200);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        String tenantId = normalizeTenant(context == null ? null : context.tenantId());

        QueryWrapper<ChatAttachment> baseWrapper = new QueryWrapper<ChatAttachment>()
                .eq("tenant_id", tenantId)
                .eq("session_id", sessionId);
        if (status != null) {
            baseWrapper.eq("process_status", status);
        }
        if (fileNameLike != null && !fileNameLike.isBlank()) {
            baseWrapper.like("file_name", fileNameLike.trim());
        }
        LocalDateTime start = parseDateTime(startTime, "startTime");
        LocalDateTime end = parseDateTime(endTime, "endTime");
        if (start != null) {
            baseWrapper.ge("created_at", start);
        }
        if (end != null) {
            baseWrapper.le("created_at", end);
        }

        Long total = chatAttachmentMapper.selectCount(baseWrapper);

        QueryWrapper<ChatAttachment> pageWrapper = rebuildPageWrapper(
            tenantId,
            sessionId,
            status,
            fileNameLike,
            start,
            end,
            normalizedPageSize,
            offset
        );

        List<ChatAttachment> rows = chatAttachmentMapper.selectList(pageWrapper);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ChatAttachment row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("sessionId", row.getSessionId());
            item.put("fileName", row.getFileName());
            item.put("objectKey", row.getObjectKey());
            item.put("fileSize", row.getFileSize());
            item.put("contentType", row.getContentType());
            item.put("provider", row.getProvider());
            item.put("summary", row.getSummary());
            item.put("processStatus", row.getProcessStatus());
            item.put("createdAt", row.getCreatedAt());
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("page", normalizedPage);
        result.put("pageSize", normalizedPageSize);
        result.put("total", total == null ? 0 : total);
        result.put("count", items.size());
        result.put("items", items);
        return result;
    }

    @Override
    public Map<String, Object> getDetail(ChatContext context, Long attachmentId) {
        if (attachmentId == null || attachmentId <= 0) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "attachmentId 非法");
        }
        ChatAttachment row = loadOwnedAttachment(context, attachmentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("tenantId", row.getTenantId());
        result.put("sessionId", row.getSessionId());
        result.put("fileName", row.getFileName());
        result.put("objectKey", row.getObjectKey());
        result.put("fileSize", row.getFileSize());
        result.put("contentType", row.getContentType());
        result.put("provider", row.getProvider());
        result.put("rawText", row.getRawText());
        result.put("condensedMd", row.getCondensedMd());
        result.put("summary", row.getSummary());
        result.put("processStatus", row.getProcessStatus());
        result.put("createdAt", row.getCreatedAt());
        return result;
    }

    @Override
    public Map<String, Object> rename(ChatContext context, Long attachmentId, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "fileName 不能为空");
        }
        ChatAttachment row = loadOwnedAttachment(context, attachmentId);
        ChatAttachment update = new ChatAttachment();
        update.setId(row.getId());
        update.setFileName(fileName.trim());
        chatAttachmentMapper.updateById(update);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("fileName", fileName.trim());
        result.put("status", "renamed");
        return result;
    }

    @Override
    public Map<String, Object> delete(ChatContext context, Long attachmentId) {
        ChatAttachment row = loadOwnedAttachment(context, attachmentId);
        deleteStorageObjectIfExists(row);
        chatAttachmentMapper.deleteById(row.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("status", "deleted");
        return result;
    }

    @Override
    public Map<String, Object> deleteBatch(ChatContext context, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "attachmentIds 不能为空");
        }
        int deleted = 0;
        for (Long attachmentId : attachmentIds) {
            ChatAttachment row = loadOwnedAttachment(context, attachmentId);
            deleteStorageObjectIfExists(row);
            deleted += chatAttachmentMapper.deleteById(row.getId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("status", "deleted");
        return result;
    }

    @Override
    public AttachmentDownload download(ChatContext context, Long attachmentId) {
        ChatAttachment row = loadOwnedAttachment(context, attachmentId);
        if (row.getObjectKey() == null || row.getObjectKey().isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件对象键不存在");
        }
        String bucket = properties.getStorage().getBucket();
        ObjectStorageGateway.DownloadedObject downloaded = objectStorageGateway.downloadObject(bucket, row.getObjectKey());
        String fileName = (row.getFileName() == null || row.getFileName().isBlank()) ? ("attachment-" + row.getId()) : row.getFileName();
        String contentType = downloaded.contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = row.getContentType();
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        return new AttachmentDownload(fileName, contentType, downloaded.bytes());
    }

    private ChatAttachment loadOwnedAttachment(ChatContext context, Long attachmentId) {
        if (attachmentId == null || attachmentId <= 0) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "attachmentId 非法");
        }
        String tenantId = normalizeTenant(context == null ? null : context.tenantId());
        ChatAttachment row = chatAttachmentMapper.selectById(attachmentId);
        if (row == null || !tenantId.equals(normalizeTenant(row.getTenantId()))) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return row;
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    private void deleteStorageObjectIfExists(ChatAttachment row) {
        if (row.getObjectKey() != null && !row.getObjectKey().isBlank()) {
            String bucket = properties.getStorage().getBucket();
            objectStorageGateway.deleteObject(bucket, row.getObjectKey());
        }
    }

    private LocalDateTime parseDateTime(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATETIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignore) {
                throw new BizException(ErrorCode.INVALID_ARGUMENT, field + " 格式错误，示例：2026-04-30 23:59:59");
            }
        }
    }

    private QueryWrapper<ChatAttachment> rebuildPageWrapper(String tenantId,
                                                            String sessionId,
                                                            Integer status,
                                                            String fileNameLike,
                                                            LocalDateTime start,
                                                            LocalDateTime end,
                                                            int pageSize,
                                                            int offset) {
        QueryWrapper<ChatAttachment> wrapper = new QueryWrapper<ChatAttachment>()
                .eq("tenant_id", tenantId)
                .eq("session_id", sessionId)
                .orderByDesc("id")
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        if (status != null) {
            wrapper.eq("process_status", status);
        }
        if (fileNameLike != null && !fileNameLike.isBlank()) {
            wrapper.like("file_name", fileNameLike.trim());
        }
        if (start != null) {
            wrapper.ge("created_at", start);
        }
        if (end != null) {
            wrapper.le("created_at", end);
        }
        return wrapper;
    }
}
