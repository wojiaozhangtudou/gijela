package com.gijela.morpheus.pistil.support.sort;

/**
 * 非法排序字段异常（用于触发 422 校验失败响应）。
 */
public class SortFieldValidationException extends RuntimeException {
    public SortFieldValidationException(String message) {
        super(message);
    }
}
