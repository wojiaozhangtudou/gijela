package com.gijela.morpheus.chatflow.controller;

import com.gijela.morpheus.chatflow.dto.ChatflowCompletionDTO;
import com.gijela.morpheus.chatflow.dto.ChatflowSessionSaveDTO;
import com.gijela.morpheus.chatflow.service.ChatflowConversationService;
import com.gijela.morpheus.chatflow.vo.ChatflowCompletionVO;
import com.gijela.morpheus.chatflow.vo.ChatflowMessagePageVO;
import com.gijela.morpheus.chatflow.vo.ChatflowSessionPageVO;
import com.gijela.morpheus.chatflow.vo.ChatflowSessionVO;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.common.enums.ErrorCode;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/chat-flow/chat")
public class ChatflowConversationController {

    private static final Logger logger = LoggerFactory.getLogger(ChatflowConversationController.class);

    private final ChatflowConversationService conversationService;

    public ChatflowConversationController(ChatflowConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/sessions")
    public ApiResponse<ChatflowSessionPageVO> listSessions(@RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                                           @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(conversationService.listSessions(keyword, pageNum, pageSize));
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatflowSessionVO> createSession(@RequestBody @Valid ChatflowSessionSaveDTO dto) {
        try {
            return ApiResponse.ok(conversationService.createSession(dto));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage(), null);
        } catch (IllegalStateException ex) {
            return ApiResponse.fail(ErrorCode.ILLEGAL_STATE.getCode(), ex.getMessage(), null);
        }
    }

    @PutMapping("/sessions/{sessionId}")
    public ApiResponse<ChatflowSessionVO> updateSession(@PathVariable String sessionId,
                                                        @RequestBody @Valid ChatflowSessionSaveDTO dto) {
        try {
            return ApiResponse.ok(conversationService.updateSession(sessionId, dto));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage(), null);
        } catch (IllegalStateException ex) {
            return ApiResponse.fail(ErrorCode.ILLEGAL_STATE.getCode(), ex.getMessage(), null);
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        try {
            conversationService.deleteSession(sessionId);
            return ApiResponse.ok(null);
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatflowMessagePageVO> listMessages(@PathVariable String sessionId,
                                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                                           @RequestParam(defaultValue = "50") Integer pageSize) {
        try {
            return ApiResponse.ok(conversationService.listMessages(sessionId, pageNum, pageSize));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @PostMapping("/completions")
    public ApiResponse<ChatflowCompletionVO> completion(@RequestBody @Valid ChatflowCompletionDTO dto) {
        try {
            return ApiResponse.ok(conversationService.completion(dto));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage(), null);
        } catch (IllegalStateException ex) {
            return ApiResponse.fail(ErrorCode.ILLEGAL_STATE.getCode(), ex.getMessage(), null);
        } catch (Exception ex) {
            logger.error("工作流执行异常, sessionId={}", dto.getSessionId(), ex);
            return ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(), "服务内部错误", null);
        }
    }
}
