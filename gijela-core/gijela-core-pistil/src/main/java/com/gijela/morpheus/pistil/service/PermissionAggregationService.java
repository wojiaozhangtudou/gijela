package com.gijela.morpheus.pistil.service;

import java.util.Set;

/** 聚合用户角色/菜单权限并写入 Redis */
public interface PermissionAggregationService {
    /**
     * 聚合并缓存权限
     * @param userId 用户ID
     * @param username 用户名（用于 Redis key）
     * @return 权限集合（包含 ROLE_ 前缀角色 & 功能权限）
     */
    Set<String> aggregateAndCache(Long userId, String username);
}
