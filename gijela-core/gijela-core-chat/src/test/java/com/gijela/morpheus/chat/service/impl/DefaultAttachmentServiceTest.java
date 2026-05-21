package com.gijela.morpheus.chat.service.impl;

import com.gijela.morpheus.chat.adapter.extractor.FileContentExtractor;
import com.gijela.morpheus.chat.adapter.storage.ObjectStorageGateway;
import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.entity.ChatAttachment;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.mapper.ChatAttachmentMapper;
import com.gijela.morpheus.chat.service.AttachmentSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAttachmentServiceTest {

    @Mock
    private ObjectStorageGateway objectStorageGateway;

    @Mock
    private ChatAttachmentMapper chatAttachmentMapper;

    @Mock
    private AttachmentSummaryService attachmentSummaryService;

    private DefaultAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAttachmentService(
                objectStorageGateway,
                chatAttachmentMapper,
                new FileContentExtractor(),
                attachmentSummaryService,
                new ChatModuleProperties()
        );
    }

    @Test
    void upload_shouldInsertAttachmentMetadataWhenStorageSucceeds() {
        ChatContext context = new ChatContext("tenant-a", "req-1", "session-1");
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        when(objectStorageGateway.upload(any(), any())).thenReturn(Map.of(
                "objectKey", "tenant-a/session-1/20260429/uuid.txt",
                "bucket", "chat-attachments",
                "provider", "rustfs",
                "size", 5L,
                "fileName", "demo.txt"
        ));

        Map<String, Object> result = service.upload(context, file, null);

        assertEquals("tenant-a/session-1/20260429/uuid.txt", result.get("objectKey"));
        ArgumentCaptor<ChatAttachment> captor = ArgumentCaptor.forClass(ChatAttachment.class);
        verify(chatAttachmentMapper).insert(captor.capture());
        ChatAttachment saved = captor.getValue();
        assertEquals("tenant-a", saved.getTenantId());
        assertEquals("session-1", saved.getSessionId());
        assertEquals("demo.txt", saved.getFileName());
        assertEquals("rustfs", saved.getProvider());
    }

    @Test
    void upload_shouldNotInsertMetadataWhenStorageFails() {
        ChatContext context = new ChatContext("tenant-a", "req-1", "session-1");
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        when(objectStorageGateway.upload(any(), any())).thenThrow(new RuntimeException("storage down"));

        assertThrows(RuntimeException.class, () -> service.upload(context, file, null));
        verify(chatAttachmentMapper, never()).insert(org.mockito.ArgumentMatchers.<ChatAttachment>any());
    }
}
