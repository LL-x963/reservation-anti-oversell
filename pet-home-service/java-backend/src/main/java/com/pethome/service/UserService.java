package com.pethome.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.entity.User;
import com.pethome.vo.LoginVO;
import com.pethome.vo.UserProfileVO;

/**
 * C 端用户服务
 *
 * @author pet-home
 */
public interface UserService extends IService<User> {

    /**
     * openid 登录，不存在则自动注册
     */
    LoginVO login(String openid);

    /**
     * 查询当前登录用户信息
     */
    UserProfileVO getProfile(Long userId);

    /**
     * 退出登录
     */
    void logout(Long userId);

}
