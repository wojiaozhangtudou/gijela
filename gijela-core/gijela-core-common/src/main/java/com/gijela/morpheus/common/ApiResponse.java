package com.gijela.morpheus.common;

import com.gijela.morpheus.common.enums.ErrorCode;
import org.slf4j.MDC;

/** 统一响应包装 */
public class ApiResponse<T> {
    private int code;
    private String msg;
    private T data;
    private Long timestamp;
    private String traceId;

    public ApiResponse() {
    }

    public ApiResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.traceId = MDC.get("traceId");
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getDefaultMsg(), data);
    }

    public static <T> ApiResponse<T> ok(String msg, T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), msg, data);
    }

    public static <T> ApiResponse<T> fail(String msg, T data) {
        return new ApiResponse<>(1, msg, data);
    }

    public static <T> ApiResponse<T> fail(int code, String msg, T data) {
        return new ApiResponse<>(code, msg, data);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code) {
        return new ApiResponse<>(code.getCode(), code.getDefaultMsg(), null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(code.getCode(), message, null);
    }

    public static <T> ApiResponse<T> of(ErrorCode code, T data) {
        return new ApiResponse<>(code.getCode(), code.getDefaultMsg(), data);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
