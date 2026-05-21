package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.chat.domain.vo.ChatContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AttachmentService {

    record AttachmentDownload(String fileName, String contentType, byte[] bytes) {
    }

    Map<String, Object> upload(ChatContext context, MultipartFile file, String summaryModel);

    Map<String, Object> getStatus(Long attachmentId);

    Map<String, Object> listBySession(ChatContext context,
                                      String sessionId,
                                      Integer page,
                                      Integer pageSize,
                                      Integer status,
                                      String fileNameLike,
                                      String startTime,
                                      String endTime);

    Map<String, Object> getDetail(ChatContext context, Long attachmentId);

    Map<String, Object> rename(ChatContext context, Long attachmentId, String fileName);

    Map<String, Object> delete(ChatContext context, Long attachmentId);

    Map<String, Object> deleteBatch(ChatContext context, java.util.List<Long> attachmentIds);

    AttachmentDownload download(ChatContext context, Long attachmentId);
}
