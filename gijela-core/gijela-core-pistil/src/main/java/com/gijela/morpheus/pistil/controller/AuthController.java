package com.gijela.morpheus.pistil.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.security.config.JwtProperties;
import com.gijela.morpheus.pistil.domain.dto.LoginSessionPageDTO;
import com.gijela.morpheus.pistil.domain.dto.LoginDTO;
import com.gijela.morpheus.pistil.domain.vo.LoginVO;
import com.gijela.morpheus.pistil.domain.vo.LoginSessionVO;
import com.gijela.morpheus.pistil.domain.vo.UserVO;
import com.gijela.morpheus.pistil.domain.vo.MenuTreeVO;
import com.gijela.morpheus.pistil.service.IAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证")
@Validated
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    @Autowired
    private IAuthService authService;
    @Autowired
    private JwtProperties jwtProperties;

    @PostMapping("/login")
    @Operation(summary = "登录")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request, HttpServletResponse response) {
        String clientLabel = resolveClientLabel(request);
        String deviceNo = resolveDeviceNo(request);
        String loginIp = resolveClientIp(request);
        LoginVO vo = authService.login(dto, clientLabel, deviceNo, loginIp);
        writeRefreshCookie(response, vo.getRefreshToken(), request.isSecure());
        vo.setRefreshToken(null);
        return ApiResponse.ok(vo);
    }

    @GetMapping("/me")
    @Operation(summary = "当前登录用户详情")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<UserVO> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(authService.currentUser(authorization));
    }

    @GetMapping("/me/menus")
    @Operation(summary = "当前登录用户有权限的菜单树")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<List<MenuTreeVO>> myMenus(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(authService.currentUserMenus(authorization));
    }

    @GetMapping("/perms")
    @Operation(summary = "当前登录用户权限标识列表")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<List<String>> myPerms(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(authService.currentUserPerms(authorization));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 access token（使用 refresh token）")
    public ApiResponse<LoginVO> refresh(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(authorization, request);
        LoginVO vo = authService.refresh(refreshToken);
        writeRefreshCookie(response, vo.getRefreshToken(), request.isSecure());
        vo.setRefreshToken(null);
        return ApiResponse.ok(vo);
    }

    @PostMapping("/logout")
    @Operation(summary = "注销 (递增 tokenVersion 使当前 token 失效)")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        authService.logout(authorization);
        clearRefreshCookie(response, request.isSecure());
        return ApiResponse.ok(null);
    }

    @GetMapping("/sessions")
    @Operation(summary = "查询登录会话列表（userId 为空时查询全部）")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<List<LoginSessionVO>> sessions(@RequestParam(value = "userId", required = false) Long userId) {
        return ApiResponse.ok(authService.listUserSessions(userId));
    }

    @PostMapping("/sessions/page")
    @Operation(summary = "分页查询登录会话（支持 userId / username）")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<Page<LoginSessionVO>> sessionsPage(@Valid @RequestBody LoginSessionPageDTO dto) {
        return ApiResponse.ok(authService.pageSessions(dto));
    }

    @PostMapping("/sessions/{sessionId}/kickout")
    @Operation(summary = "踢出单个会话")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<Void> kickout(@PathVariable("sessionId") String sessionId) {
        authService.kickoutSession(sessionId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/sessions/kickout-all")
    @Operation(summary = "踢出用户全部会话")
    @SecurityRequirement(name = "bearer-jwt")
    public ApiResponse<Void> kickoutAll(@RequestParam("userId") Long userId) {
        authService.kickoutAllSessions(userId);
        return ApiResponse.ok(null);
    }

    private String resolveRefreshToken(String authorization, HttpServletRequest request) {
        if (StringUtils.hasText(authorization)) {
            return authorization;
        }
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(c.getName()) && StringUtils.hasText(c.getValue())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void writeRefreshCookie(HttpServletResponse response, String refreshToken, boolean secure) {
        if (!StringUtils.hasText(refreshToken)) return;
        long maxAgeSeconds = Math.max(1L, jwtProperties.getRefreshExpDays() * 24L * 60L * 60L);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path("/api/v1/auth")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveClientLabel(HttpServletRequest request) {
        String fromHeader = request.getHeader("X-Client-Label");
        if (StringUtils.hasText(fromHeader)) {
            return fromHeader;
        }
        String ua = request.getHeader("User-Agent");
        return StringUtils.hasText(ua) ? ua : "web";
    }

    private String resolveDeviceNo(HttpServletRequest request) {
        String fromHeader = request.getHeader("X-Device-Id");
        return StringUtils.hasText(fromHeader) ? fromHeader : null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            String[] parts = xff.split(",");
            if (parts.length > 0 && StringUtils.hasText(parts[0])) {
                return parts[0].trim();
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
