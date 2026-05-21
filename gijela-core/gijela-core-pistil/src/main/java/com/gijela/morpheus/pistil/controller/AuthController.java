package com.gijela.morpheus.pistil.controller;

import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.pistil.domain.dto.LoginDTO;
import com.gijela.morpheus.pistil.domain.vo.LoginVO;
import com.gijela.morpheus.pistil.domain.vo.UserVO;
import com.gijela.morpheus.pistil.domain.vo.MenuTreeVO;
import com.gijela.morpheus.pistil.service.IAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证")
@Validated
public class AuthController {

    @Autowired
    private IAuthService authService;

    @PostMapping("/login")
    @Operation(summary = "登录")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResponse.ok(authService.login(dto));
    }

    @GetMapping("/me")
    @Operation(summary = "当前登录用户详情")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<UserVO> me(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @RequestParam(value = "token", required = false) String tokenParam) {
        String token = authorization != null ? authorization : tokenParam; // 兼容 query 传参
        return ApiResponse.ok(authService.currentUser(token));
    }

    @GetMapping("/me/menus")
    @Operation(summary = "当前登录用户有权限的菜单树")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<List<MenuTreeVO>> myMenus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @RequestParam(value = "token", required = false) String tokenParam) {
        String token = authorization != null ? authorization : tokenParam;
        return ApiResponse.ok(authService.currentUserMenus(token));
    }

    @GetMapping("/perms")
    @Operation(summary = "当前登录用户权限标识列表")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<List<String>> myPerms(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestParam(value = "token", required = false) String tokenParam) {
        String token = authorization != null ? authorization : tokenParam;
        return ApiResponse.ok(authService.currentUserPerms(token));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 access token（使用 refresh token）")
    public ApiResponse<LoginVO> refresh(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestParam(value = "token", required = false) String tokenParam) {
        String token = authorization != null ? authorization : tokenParam; // 支持 header 或 query
        return ApiResponse.ok(authService.refresh(token));
    }

    @PostMapping("/logout")
    @Operation(summary = "注销 (递增 tokenVersion 使当前 token 失效)")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam(value = "token", required = false) String tokenParam) {
        String token = authorization != null ? authorization : tokenParam;
        authService.logout(token);
        return ApiResponse.ok(null);
    }
}
