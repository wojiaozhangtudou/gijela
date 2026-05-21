package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.adapter.storage.ObjectStorageGateway;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.service.StorageAdminService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultStorageAdminService implements StorageAdminService {

    private final ObjectStorageGateway objectStorageGateway;

    public DefaultStorageAdminService(ObjectStorageGateway objectStorageGateway) {
        this.objectStorageGateway = objectStorageGateway;
    }

    @Override
    public Map<String, Object> listBuckets(ChatContext context) {
        List<Map<String, Object>> buckets = objectStorageGateway.listBuckets();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", context == null ? null : context.tenantId());
        result.put("buckets", buckets);
        result.put("count", buckets.size());
        return result;
    }

    @Override
    public Map<String, Object> createBucket(ChatContext context, String bucket) {
        Map<String, Object> result = new LinkedHashMap<>(objectStorageGateway.createBucket(bucket));
        result.put("tenantId", context == null ? null : context.tenantId());
        return result;
    }

    @Override
    public Map<String, Object> deleteBucket(ChatContext context, String bucket) {
        Map<String, Object> result = new LinkedHashMap<>(objectStorageGateway.deleteBucket(bucket));
        result.put("tenantId", context == null ? null : context.tenantId());
        return result;
    }

    @Override
    public Map<String, Object> listObjects(ChatContext context, String bucket, String prefix, Integer maxKeys) {
        List<Map<String, Object>> objects = objectStorageGateway.listObjects(bucket, prefix, maxKeys);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", context == null ? null : context.tenantId());
        result.put("bucket", bucket);
        result.put("prefix", prefix);
        result.put("objects", objects);
        result.put("count", objects.size());
        return result;
    }

    @Override
    public Map<String, Object> deleteObject(ChatContext context, String bucket, String objectKey) {
        Map<String, Object> result = new LinkedHashMap<>(objectStorageGateway.deleteObject(bucket, objectKey));
        result.put("tenantId", context == null ? null : context.tenantId());
        return result;
    }

    @Override
    public Map<String, Object> uploadObject(ChatContext context, String bucket, String objectKey, MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<>(objectStorageGateway.uploadObject(bucket, objectKey, file));
        result.put("tenantId", context == null ? null : context.tenantId());
        return result;
    }

    @Override
    public StorageObjectDownload downloadObject(ChatContext context, String bucket, String objectKey) {
        ObjectStorageGateway.DownloadedObject downloaded = objectStorageGateway.downloadObject(bucket, objectKey);
        String fileName = objectKey;
        if (fileName != null && fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "object.bin";
        }
        String contentType = downloaded.contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        return new StorageObjectDownload(fileName, contentType, downloaded.bytes());
    }
}
