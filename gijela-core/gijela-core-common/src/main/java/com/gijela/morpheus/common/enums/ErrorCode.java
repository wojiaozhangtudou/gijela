package com.gijela.morpheus.common.enums;

/** 统一错误码枚举 */
public enum ErrorCode {
    SUCCESS(0, "OK"),

    INVALID_ARGUMENT(1001, "参数错误"),
    VALIDATION_FAILED(1002, "校验失败"),
    METHOD_NOT_SUPPORTED(1003, "不支持的请求"),

    UNAUTHORIZED(2001, "未登录"),
    TOKEN_INVALID(2002, "令牌无效或过期"),
    FORBIDDEN(2003, "无访问权限"),
    SESSION_INVALIDATED(2004, "会话已失效"),

    NOT_FOUND(3001, "资源不存在"),
    CONFLICT(3002, "资源冲突"),
    ILLEGAL_STATE(3003, "资源状态不允许此操作"),
    RATE_LIMITED(3004, "请求过于频繁"),
    GRAPH_FILE_TOO_LARGE(3101, "图谱文件过大"),
    GRAPH_TEXT_TOO_LONG(3102, "图谱文本过长"),
    GRAPH_IMPORT_CONSTRAINT_CONFLICT(3103, "图谱导入约束冲突"),
    GRAPH_PREVIEW_NOT_FOUND(3104, "图谱预览不存在或已失效"),
    GRAPH_MODULE_DISABLED(3105, "图谱模块未启用"),
    GRAPH_MISSING_IDEMPOTENCY_KEY(3106, "缺少导入幂等键"),

    INTERNAL_ERROR(5000, "系统错误"),
    DB_ERROR(5001, "数据库错误"),
    NETWORK_IO_ERROR(5002, "网络或IO异常");

    private final int code;
    private final String defaultMsg;

    ErrorCode(int code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMsg() {
        return defaultMsg;
    }
}
