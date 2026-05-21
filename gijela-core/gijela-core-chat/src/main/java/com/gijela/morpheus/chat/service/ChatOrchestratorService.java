package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.chat.domain.dto.ChatCompletionRequest;
import com.gijela.morpheus.chat.domain.dto.ChatCompletionResponse;
import com.gijela.morpheus.chat.domain.dto.StreamChatRequest;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.gijela.morpheus.chat.domain.vo.ChatEventVO;

import java.util.List;
import java.util.function.Consumer;

public interface ChatOrchestratorService {

    ChatCompletionResponse complete(ChatContext context, ChatCompletionRequest request);

    void stream(ChatContext context, StreamChatRequest request, Consumer<ChatEventVO> consumer);

    List<String> listSkills();
}
