package com.gijela.morpheus.chatflow.service;

import com.gijela.morpheus.chatflow.dto.LlmModelSaveDTO;
import com.gijela.morpheus.chatflow.vo.LlmModelVO;

import java.util.List;

public interface LlmModelService {

    List<LlmModelVO> list(String name, Integer enabled);

    LlmModelVO getById(Long id);

    LlmModelVO create(LlmModelSaveDTO dto);

    LlmModelVO update(Long id, LlmModelSaveDTO dto);

    void delete(Long id);
}
