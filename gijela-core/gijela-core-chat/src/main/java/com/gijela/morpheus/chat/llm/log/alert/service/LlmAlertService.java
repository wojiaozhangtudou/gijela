package com.gijela.morpheus.chat.llm.log.alert.service;

import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertEventActionRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertEventPageRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.request.AlertRuleUpsertRequest;
import com.gijela.morpheus.chat.llm.log.alert.dto.response.AlertEventVO;
import com.gijela.morpheus.chat.llm.log.alert.dto.response.AlertRuleVO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface LlmAlertService {

    List<AlertRuleVO> listRules(String tenantId);

    Long createRule(String tenantId, String operator, AlertRuleUpsertRequest request);

    Long updateRule(Long id, String tenantId, String operator, AlertRuleUpsertRequest request);

    void toggleRule(Long id, String tenantId, String operator, boolean enabled);

    Page<AlertEventVO> pageEvents(AlertEventPageRequest request);

    void ackEvent(Long id, String tenantId, AlertEventActionRequest request);

    void resolveEvent(Long id, String tenantId, AlertEventActionRequest request);

    Long triggerRule(Long id, String tenantId, String operator);

    void triggerEnabledRules();
}
