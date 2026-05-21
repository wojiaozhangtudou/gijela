package com.gijela.morpheus.llm.sdk.observability;

/**
 * 预算事件发布器。
 */
public interface BudgetEventPublisher {

    void publish(BudgetEvent event);
}
