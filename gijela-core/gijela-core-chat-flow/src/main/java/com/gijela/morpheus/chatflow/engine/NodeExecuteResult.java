package com.gijela.morpheus.chatflow.engine;

import java.util.Map;

public class NodeExecuteResult {

    private String status;
    private Map<String, Object> outputs;
    private String errorMessage;

    public static NodeExecuteResult success(Map<String, Object> outputs) {
        NodeExecuteResult result = new NodeExecuteResult();
        result.setStatus("success");
        result.setOutputs(outputs);
        return result;
    }

    public static NodeExecuteResult failed(String errorMessage) {
        NodeExecuteResult result = new NodeExecuteResult();
        result.setStatus("failed");
        result.setErrorMessage(errorMessage);
        return result;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
