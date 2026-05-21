package com.gijela.morpheus.chat.web;

import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.common.enums.ErrorCode;

public class PromptConflictException extends BizException {

    private final Object data;

    public PromptConflictException(String message, Object data) {
        super(ErrorCode.CONFLICT, message);
        this.data = data;
    }

    public Object getData() {
        return data;
    }
}
