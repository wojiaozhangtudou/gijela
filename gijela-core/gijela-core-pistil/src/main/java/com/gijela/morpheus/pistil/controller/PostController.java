package com.gijela.morpheus.pistil.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.pistil.domain.dto.PostPageDTO;
import com.gijela.morpheus.pistil.domain.dto.IdsDTO;
import com.gijela.morpheus.pistil.domain.dto.SavePostDTO;
import com.gijela.morpheus.pistil.domain.dto.ChangeStatusDTO;
import com.gijela.morpheus.pistil.domain.vo.PostVO;
import com.gijela.morpheus.pistil.service.ISysPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "岗位管理")
@SecurityRequirement(name = "bearer-jwt")
@Validated
public class PostController {

    @Autowired
    private ISysPostService postService;

    @PostMapping("/page")
    @Operation(summary = "岗位分页查询")
    public ApiResponse<Page<PostVO>> page(@Valid @RequestBody PostPageDTO dto) {
        return ApiResponse.ok(postService.pagePosts(dto));
    }

    @PostMapping
    @Operation(summary = "新增或编辑岗位")
    public ApiResponse<Long> save(@Valid @RequestBody SavePostDTO dto) {
        return ApiResponse.ok(postService.savePost(dto));
    }

    @PostMapping("/delete")
    @Operation(summary = "批量删除岗位")
    public ApiResponse<Void> delete(@Valid @RequestBody IdsDTO dto) {
        postService.deletePosts(dto);
        return ApiResponse.ok(null);
    }

    @PostMapping("/status")
    @Operation(summary = "修改岗位状态")
    public ApiResponse<Void> changeStatus(@Valid @RequestBody ChangeStatusDTO dto) {
        postService.changeStatus(dto);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "岗位详情")
    public ApiResponse<PostVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(postService.getPostDetail(id));
    }
}
