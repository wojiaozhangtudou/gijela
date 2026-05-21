package com.gijela.morpheus.chatflow.service;

import com.gijela.morpheus.chatflow.dto.ChatflowCompletionDTO;
import com.gijela.morpheus.chatflow.dto.ChatflowSessionSaveDTO;
import com.gijela.morpheus.chatflow.vo.ChatflowCompletionVO;
import com.gijela.morpheus.chatflow.vo.ChatflowMessagePageVO;
import com.gijela.morpheus.chatflow.vo.ChatflowSessionPageVO;
import com.gijela.morpheus.chatflow.vo.ChatflowSessionVO;

public interface ChatflowConversationService {

    ChatflowSessionPageVO listSessions(String keyword, Integer pageNum, Integer pageSize);

    ChatflowSessionVO createSession(ChatflowSessionSaveDTO dto);

    ChatflowSessionVO updateSession(String sessionId, ChatflowSessionSaveDTO dto);

    void deleteSession(String sessionId);

    ChatflowMessagePageVO listMessages(String sessionId, Integer pageNum, Integer pageSize);

    ChatflowCompletionVO completion(ChatflowCompletionDTO dto);
}
