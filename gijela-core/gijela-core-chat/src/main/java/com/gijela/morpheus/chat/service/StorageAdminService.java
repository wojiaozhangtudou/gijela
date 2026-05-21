package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.chat.domain.vo.ChatContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface StorageAdminService {

    record StorageObjectDownload(String fileName, String contentType, byte[] bytes) {
    }

    Map<String, Object> listBuckets(ChatContext context);

    Map<String, Object> createBucket(ChatContext context, String bucket);

    Map<String, Object> deleteBucket(ChatContext context, String bucket);

    Map<String, Object> listObjects(ChatContext context, String bucket, String prefix, Integer maxKeys);

    Map<String, Object> deleteObject(ChatContext context, String bucket, String objectKey);

    Map<String, Object> uploadObject(ChatContext context, String bucket, String objectKey, MultipartFile file);

    StorageObjectDownload downloadObject(ChatContext context, String bucket, String objectKey);
}
