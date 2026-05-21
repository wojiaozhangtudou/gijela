package com.gijela.morpheus.pistil.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gijela.morpheus.pistil.domain.dto.ChangeStatusDTO;
import com.gijela.morpheus.pistil.domain.dto.PostPageDTO;
import com.gijela.morpheus.pistil.domain.dto.IdsDTO;
import com.gijela.morpheus.pistil.domain.dto.SavePostDTO;
import com.gijela.morpheus.pistil.domain.entity.SysPost;
import com.gijela.morpheus.pistil.domain.vo.PostVO;

/**
 * <p>
 * 岗位表 服务类
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
public interface ISysPostService extends IService<SysPost> {

    Page<PostVO> pagePosts(PostPageDTO dto);

    Long savePost(SavePostDTO dto);

    void deletePosts(IdsDTO dto);

    PostVO getPostDetail(Long id);

    void changeStatus(ChangeStatusDTO dto);
}
