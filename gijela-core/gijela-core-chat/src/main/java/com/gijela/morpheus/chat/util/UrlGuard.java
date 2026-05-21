package com.gijela.morpheus.chat.util;

import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

/**
 * MCP Server endpoint URL 白名单校验。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>scheme 仅允许 http / https</li>
 *   <li>scheme = http 时仅允许 localhost / 127.0.0.1 / ::1</li>
 *   <li>host 解析后若为私有 / 链路本地 / loopback 网段，需 {@code allowPrivateNetwork=true} 才放行</li>
 * </ul>
 */
public final class UrlGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private UrlGuard() {}

    public static void validate(String url, boolean allowPrivateNetwork) {
        if (url == null || url.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "endpoint 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "endpoint 不是合法 URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT,
                    "endpoint 仅支持 http/https，实际=" + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "endpoint 缺少 host");
        }
        String hostLower = host.toLowerCase();

        // http 仅允许 localhost
        if ("http".equals(scheme) && !LOCAL_HOSTS.contains(hostLower)) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT,
                    "明文 http 仅允许 localhost / 127.0.0.1，请改用 https");
        }

        // localhost / 127.0.0.1 / ::1 显式允许，跳过私有网段二次校验
        if (LOCAL_HOSTS.contains(hostLower)) {
            return;
        }

        if (allowPrivateNetwork) {
            return;
        }

        // 私有/链路本地网段（仅按 IP 字面量判断，不主动 DNS 解析以避免 DNS rebinding 与额外延迟）
        InetAddress addr = parseLiteralIp(hostLower);
        if (addr == null) {
            // 主机名 → 留给运行期 OkHttp 解析；阶段 A 不在此处主动 lookup
            return;
        }
        if (isBlocked(addr)) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT,
                    "endpoint 指向私有/链路本地网段，已被拦截。如确需访问，请把 chat.mcp.allow-private-network 置为 true");
        }
    }

    private static InetAddress parseLiteralIp(String host) {
        // 仅当 host 是 IP 字面量时返回 InetAddress（不会触发 DNS 查询）
        try {
            if (host.contains(":") || isDottedIpv4(host)) {
                return InetAddress.getByName(host);
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private static boolean isDottedIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        for (String p : parts) {
            try {
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlocked(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress();
    }
}
