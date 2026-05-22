package com.gijela.morpheus.pistil.service;

import com.gijela.morpheus.pistil.domain.vo.LoginSessionVO;

import java.util.List;

public interface RefreshTokenService {
    String createSession(Long userId, String username, String refreshJti, long expireAt, String clientLabel, String deviceNo, String loginIp);
    boolean validateSession(String sessionId, String refreshJti);
    void refreshSession(String sessionId, String refreshJti, long expireAt);
    void touchSession(String sessionId);
    void revokeSession(String sessionId);
    void revokeAllSessions(Long userId);
    List<LoginSessionVO> listSessions(Long userId);
    List<LoginSessionVO> listAllSessions();
}
