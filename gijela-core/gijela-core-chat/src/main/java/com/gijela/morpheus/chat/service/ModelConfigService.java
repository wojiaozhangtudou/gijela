package com.gijela.morpheus.chat.service;

import com.gijela.morpheus.chat.domain.dto.model.ModelConfigSaveRequest;
import com.gijela.morpheus.chat.domain.vo.model.ModelConfigItemResponse;
import com.gijela.morpheus.chat.domain.vo.model.ModelConfigOptionResponse;

import java.util.List;
import java.util.Map;

public interface ModelConfigService {

    List<ModelConfigItemResponse> list(String tenantId, String configType);

    ModelConfigItemResponse create(String tenantId, String operator, ModelConfigSaveRequest request);

    ModelConfigItemResponse update(String tenantId, Long id, String operator, ModelConfigSaveRequest request);

    void delete(String tenantId, Long id);

    List<ModelConfigOptionResponse> listOptions(String tenantId, String configType);

    Map<String, Object> testConnection(String tenantId, Long id);
}
