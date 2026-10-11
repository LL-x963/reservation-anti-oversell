package com.pethome.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.UserLoginRequest;
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
     * 用户登录，支持两种方式（二选一，均支持首次登录自动注册）：
     * <ul>
     *   <li>openid 微信登录；</li>
     *   <li>手机号 + 密码登录。</li>
     * </ul>
     *
     * @param request 登录请求（openid 或 手机号 + 密码）
     * @return token 与登录用户信息
     */
    LoginVO login(UserLoginRequest request);

    /**
     * 查询当前登录用户信息
     */
    UserProfileVO getProfile(Long userId);

    /**
     * 退出登录
     */
    void logout(Long userId);

}
