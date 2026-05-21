package com.gijela.morpheus.chatflow.controller;

import com.gijela.morpheus.chatflow.dto.LlmModelSaveDTO;
import com.gijela.morpheus.chatflow.service.LlmModelService;
import com.gijela.morpheus.chatflow.vo.LlmModelVO;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.common.enums.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/chat-flow/llm-models")
public class LlmModelController {

    private final LlmModelService llmModelService;

    public LlmModelController(LlmModelService llmModelService) {
        this.llmModelService = llmModelService;
    }

    @GetMapping
    public ApiResponse<List<LlmModelVO>> list(@RequestParam(required = false) String name,
                                              @RequestParam(required = false) Integer enabled) {
        return ApiResponse.ok(llmModelService.list(name, enabled));
    }

    @GetMapping("/{id}")
    public ApiResponse<LlmModelVO> get(@PathVariable Long id) {
        try {
            return ApiResponse.ok(llmModelService.getById(id));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }

    @PostMapping
    public ApiResponse<LlmModelVO> create(@RequestBody @Valid LlmModelSaveDTO dto) {
        try {
            return ApiResponse.ok(llmModelService.create(dto));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ErrorCode.CONFLICT.getCode(), ex.getMessage(), null);
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmModelVO> update(@PathVariable Long id,
                                          @RequestBody @Valid LlmModelSaveDTO dto) {
        try {
            return ApiResponse.ok(llmModelService.update(id, dto));
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ErrorCode.CONFLICT.getCode(), ex.getMessage(), null);
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            llmModelService.delete(id);
            return ApiResponse.ok(null);
        } catch (NoSuchElementException ex) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null);
        }
    }
}
