package com.pethome.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.UserLoginRequest;
import com.pethome.dto.UserRegisterRequest;
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
     * 用户登录，支持三种方式（互斥）：
     * <ul>
     *   <li>微信小程序 code 登录（服务端换取 openid，首次登录自动建号）；</li>
     *   <li>手机号 + 密码登录（手机号必须已注册，不再自动注册）；</li>
     *   <li>openid 直登（仅本地开发 / 自动化测试，生产禁用）。</li>
     * </ul>
     *
     * @param request 登录请求
     * @return token 与登录用户信息
     */
    LoginVO login(UserLoginRequest request);

    /**
     * 手机号 + 密码注册
     *
     * @param request 注册请求
     */
    void register(UserRegisterRequest request);

    /**
     * 查询当前登录用户信息
     */
    UserProfileVO getProfile(Long userId);

    /**
     * 退出登录
     */
    void logout(Long userId);

}
