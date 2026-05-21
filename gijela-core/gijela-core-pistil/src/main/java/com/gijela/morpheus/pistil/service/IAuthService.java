package com.gijela.morpheus.pistil.service;

import com.gijela.morpheus.pistil.domain.dto.LoginDTO;
import com.gijela.morpheus.pistil.domain.vo.LoginVO;
import com.gijela.morpheus.pistil.domain.vo.UserVO;
import com.gijela.morpheus.pistil.domain.vo.MenuTreeVO;

import java.util.List;

/** 认证服务 */
public interface IAuthService {
    LoginVO login(LoginDTO dto);
    // 解析 token 并返回用户详情（从数据库查询）
    UserVO currentUser(String token);
    // 解析 token 并返回用户有权限的菜单树
    List<MenuTreeVO> currentUserMenus(String token);
    // 解析 token 并返回当前用户所有权限标识（perms 列表）
    List<String> currentUserPerms(String token);
    // 使用 refresh token 刷新 access token（并返回新的 refresh token）
    LoginVO refresh(String refreshToken);
    /** 注销(使当前 access token 失效，通过递增 tokenVersion) */
    void logout(String token);
}
