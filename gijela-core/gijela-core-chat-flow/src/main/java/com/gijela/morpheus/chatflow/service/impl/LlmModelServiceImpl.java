package com.gijela.morpheus.chatflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gijela.morpheus.chatflow.domain.entity.LlmModelEntity;
import com.gijela.morpheus.chatflow.dto.LlmModelSaveDTO;
import com.gijela.morpheus.chatflow.mapper.LlmModelMapper;
import com.gijela.morpheus.chatflow.service.LlmModelService;
import com.gijela.morpheus.chatflow.vo.LlmModelVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class LlmModelServiceImpl implements LlmModelService {

    private final LlmModelMapper llmModelMapper;

    public LlmModelServiceImpl(LlmModelMapper llmModelMapper) {
        this.llmModelMapper = llmModelMapper;
    }

    @Override
    public List<LlmModelVO> list(String name, Integer enabled) {
        LambdaQueryWrapper<LlmModelEntity> query = new LambdaQueryWrapper<LlmModelEntity>()
                .eq(LlmModelEntity::getDeleted, 0)
                .orderByDesc(LlmModelEntity::getUpdatedAt)
                .orderByDesc(LlmModelEntity::getId);

        if (StringUtils.hasText(name)) {
            String keyword = name.trim();
            query.and(q -> q.like(LlmModelEntity::getDisplayName, keyword)
                    .or().like(LlmModelEntity::getModelKey, keyword)
                    .or().like(LlmModelEntity::getTargetModel, keyword));
        }
        if (enabled != null) {
            query.eq(LlmModelEntity::getEnabled, enabled);
        }

        return llmModelMapper.selectList(query).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public LlmModelVO getById(Long id) {
        return toVO(loadModel(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmModelVO create(LlmModelSaveDTO dto) {
        assertUniqueModelKey(dto.getModelKey(), null);

        LocalDateTime now = LocalDateTime.now();
        LlmModelEntity entity = new LlmModelEntity();
        entity.setModelKey(dto.getModelKey().trim());
        entity.setDisplayName(dto.getDisplayName().trim());
        entity.setProvider(requireOpenAiProvider(dto.getProvider()));
        entity.setTargetModel(dto.getTargetModel().trim());
        entity.setBaseUrl(dto.getBaseUrl().trim());
        entity.setApiKey(dto.getApiKey().trim());
        entity.setEnabled(normalizeEnabled(dto.getEnabled()));
        entity.setDefaultTemperature(dto.getDefaultTemperature());
        entity.setDefaultMaxTokens(dto.getDefaultMaxTokens());
        entity.setRemark(dto.getRemark());
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        llmModelMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmModelVO update(Long id, LlmModelSaveDTO dto) {
        LlmModelEntity existing = loadModel(id);
        assertUniqueModelKey(dto.getModelKey(), id);

        existing.setModelKey(dto.getModelKey().trim());
        existing.setDisplayName(dto.getDisplayName().trim());
        existing.setProvider(requireOpenAiProvider(dto.getProvider()));
        existing.setTargetModel(dto.getTargetModel().trim());
        existing.setBaseUrl(dto.getBaseUrl().trim());
        existing.setApiKey(dto.getApiKey().trim());
        existing.setEnabled(normalizeEnabled(dto.getEnabled()));
        existing.setDefaultTemperature(dto.getDefaultTemperature());
        existing.setDefaultMaxTokens(dto.getDefaultMaxTokens());
        existing.setRemark(dto.getRemark());
        existing.setUpdatedBy("system");
        existing.setUpdatedAt(LocalDateTime.now());
        llmModelMapper.updateById(existing);
        return toVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LlmModelEntity existing = loadModel(id);
        existing.setDeleted(1);
        existing.setUpdatedBy("system");
        existing.setUpdatedAt(LocalDateTime.now());
        llmModelMapper.updateById(existing);
    }

    private LlmModelEntity loadModel(Long id) {
        LlmModelEntity entity = llmModelMapper.selectById(id);
        if (entity == null || Integer.valueOf(1).equals(entity.getDeleted())) {
            throw new NoSuchElementException("模型不存在: " + id);
        }
        return entity;
    }

    private void assertUniqueModelKey(String modelKey, Long excludeId) {
        if (!StringUtils.hasText(modelKey)) {
            throw new IllegalArgumentException("模型键不能为空");
        }
        String normalized = modelKey.trim();
        LambdaQueryWrapper<LlmModelEntity> query = new LambdaQueryWrapper<LlmModelEntity>()
                .eq(LlmModelEntity::getDeleted, 0)
                .eq(LlmModelEntity::getModelKey, normalized);
        if (excludeId != null) {
            query.ne(LlmModelEntity::getId, excludeId);
        }
        if (llmModelMapper.selectCount(query) > 0) {
            throw new IllegalArgumentException("模型键已存在: " + normalized);
        }
    }

    private Integer normalizeEnabled(Integer enabled) {
        if (enabled == null) {
            return 1;
        }
        return enabled == 0 ? 0 : 1;
    }

    private String requireOpenAiProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new IllegalArgumentException("供应商不能为空");
        }
        String normalized = provider.trim().toLowerCase();
        if (!"openai".equals(normalized)) {
            throw new IllegalArgumentException("当前仅支持 openai 接口");
        }
        return normalized;
    }

    private LlmModelVO toVO(LlmModelEntity entity) {
        LlmModelVO vo = new LlmModelVO();
        vo.setId(entity.getId());
        vo.setModelKey(entity.getModelKey());
        vo.setDisplayName(entity.getDisplayName());
        vo.setProvider(entity.getProvider());
        vo.setTargetModel(entity.getTargetModel());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setEnabled(entity.getEnabled());
        vo.setDefaultTemperature(entity.getDefaultTemperature());
        vo.setDefaultMaxTokens(entity.getDefaultMaxTokens());
        vo.setRemark(entity.getRemark());
        vo.setUpdatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString());
        return vo;
    }
}
