package com.gijela.morpheus.pistil.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gijela.morpheus.pistil.domain.dto.*;
import com.gijela.morpheus.pistil.domain.entity.SysUser;
import com.gijela.morpheus.pistil.domain.vo.UserVO;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
public interface ISysUserService extends IService<SysUser> {
    Page<UserVO> pageUsers(UserPageDTO dto);
    Long saveUser(SaveUserDTO dto);
    void deleteUsers(IdsDTO dto);
    void changeStatus(ChangeStatusDTO dto);
    void resetPassword(ResetPwdDTO dto);
    UserVO getUserDetail(Long id);
    void updateAvatar(UpdateAvatarDTO dto);
    String getAvatar(Long userId);
}
