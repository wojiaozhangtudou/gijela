package com.gijela.morpheus.chat.llm.log.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gijela.morpheus.chat.llm.log.alert.domain.entity.LlmAlertEvent;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmAlertEventMapper extends BaseMapper<LlmAlertEvent> {
}
