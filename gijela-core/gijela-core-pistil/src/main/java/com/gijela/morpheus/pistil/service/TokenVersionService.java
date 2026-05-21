package com.gijela.morpheus.pistil.service;

/** 管理用户 tokenVersion 的服务 (Redis 为主) */
public interface TokenVersionService {
    /** 获取当前版本，可能为 null */
    Integer get(String username);
    /** 若不存在则初始化为 initial(可为 null 表示 0)，返回最终版本 */
    Integer ensureInitialized(String username, Integer initial);
    /** 递增并返回新版本 */
    Integer increment(String username);
}

