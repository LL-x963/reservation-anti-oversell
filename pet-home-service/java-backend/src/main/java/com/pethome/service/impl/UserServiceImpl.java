package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.client.WechatAuthClient;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;
import com.pethome.common.security.LoginGuard;
import com.pethome.common.security.PasswordPolicy;
import com.pethome.config.AuthProperties;
import com.pethome.dto.UserLoginRequest;
import com.pethome.dto.UserRegisterRequest;
import com.pethome.entity.User;
import com.pethome.mapper.UserMapper;
import com.pethome.service.TokenService;
import com.pethome.service.UserService;
import com.pethome.vo.LoginVO;
import com.pethome.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C 端用户服务实现
 *
 * <p>登录安全要点：</p>
 * <ul>
 *   <li>微信登录只接受一次性 code，由服务端换取 openid，客户端无法伪造身份；</li>
 *   <li>手机号登录不再「不存在即注册」，未注册账号统一返回「手机号或密码错误」；</li>
 *   <li>连续失败按手机号锁定，防止撞库。</li>
 * </ul>
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String LOGIN_ACCOUNT_PREFIX = "user:";

    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final WechatAuthClient wechatAuthClient;
    private final AuthProperties authProperties;
    private final LoginGuard loginGuard;

    @Override
    public LoginVO login(UserLoginRequest request) {
        boolean hasCode = StringUtils.isNotBlank(request.getCode());
        boolean hasOpenid = StringUtils.isNotBlank(request.getOpenid());
        boolean hasPhone = StringUtils.isNotBlank(request.getPhone());

        int modes = (hasCode ? 1 : 0) + (hasOpenid ? 1 : 0) + (hasPhone ? 1 : 0);
        if (modes == 0) {
            throw new BusinessException("请传入微信 code、openid 或 手机号+密码");
        }
        // 同时传多种方式属于非法请求，避免出现「用 openid 绕过密码」这类歧义
        if (modes > 1) {
            throw new BusinessException("微信 code、openid、手机号+密码三种登录方式只能二选一");
        }
        if (hasCode) {
            return loginByWechatCode(request.getCode().trim());
        }
        if (hasOpenid) {
            return loginByOpenid(request.getOpenid().trim());
        }
        return loginByPhone(request.getPhone().trim(), request.getPassword());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterRequest request) {
        PasswordPolicy.validate(request.getPassword());
        String phone = request.getPhone().trim();
        if (getByPhone(phone) != null) {
            throw new BusinessException("该手机号已注册，请直接登录");
        }

        User user = new User();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setSex(0);
        if (StringUtils.isNotBlank(request.getName())) {
            user.setName(request.getName().trim());
        }
        try {
            save(user);
        } catch (DuplicateKeyException e) {
            // 并发注册：唯一索引兜底，返回同一提示
            throw new BusinessException("该手机号已注册，请直接登录");
        }
        log.info("手机号注册成功: userId={}, phone={}", user.getId(), phone);
    }

    @Override
    public UserProfileVO getProfile(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserProfileVO profileVO = new UserProfileVO();
        BeanUtils.copyProperties(user, profileVO);
        return profileVO;
    }

    @Override
    public void logout(Long userId) {
        tokenService.revoke(userId, UserType.USER);
    }

    /**
     * 微信小程序登录：code 由服务端换取 openid，首次登录自动建号（身份可信）
     */
    private LoginVO loginByWechatCode(String code) {
        String openid = wechatAuthClient.code2Openid(code);
        return issueByOpenid(openid, "wechat");
    }

    /**
     * openid 直登：仅限本地开发与自动化测试
     */
    private LoginVO loginByOpenid(String openid) {
        if (!authProperties.isAllowOpenidDirectLogin()) {
            throw new BusinessException("openid 直登已禁用，请使用小程序登录");
        }
        log.warn("使用 openid 直登（仅限开发/测试环境）: openid={}", openid);
        return issueByOpenid(openid, "openid-direct");
    }

    /**
     * 按 openid 查用户，不存在则建号并签发 token
     */
    private LoginVO issueByOpenid(String openid, String source) {
        User user = lambdaQuery().eq(User::getOpenid, openid).one();
        if (user == null) {
            User created = new User();
            created.setOpenid(openid);
            created.setSex(0);
            try {
                save(created);
                user = created;
                log.info("openid 首次登录，自动注册用户: userId={}, source={}", user.getId(), source);
            } catch (DuplicateKeyException e) {
                // 并发首次登录：另一请求已建号，回查即可
                user = lambdaQuery().eq(User::getOpenid, openid).one();
                if (user == null) {
                    throw new BusinessException("登录失败，请重试");
                }
            }
        }
        return tokenService.issue(user.getId(), UserType.USER, user.getName());
    }

    /**
     * 手机号 + 密码登录：账号必须已注册，失败计数达阈值后锁定
     */
    private LoginVO loginByPhone(String phone, String password) {
        if (StringUtils.isBlank(password)) {
            throw new BusinessException("请输入密码");
        }

        String accountKey = LOGIN_ACCOUNT_PREFIX + phone;
        loginGuard.checkAccountLocked(accountKey);

        User user = getByPhone(phone);
        // 账号不存在与密码错误返回同一提示，避免暴露手机号是否已注册
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(password, user.getPassword())) {
            loginGuard.onLoginFailure(accountKey);
            throw new BusinessException("手机号或密码错误");
        }

        loginGuard.onLoginSuccess(accountKey);
        return tokenService.issue(user.getId(), UserType.USER, user.getName());
    }

    /**
     * 按手机号查询用户（phone 为唯一索引，这里只取一条兜底历史脏数据）
     */
    private User getByPhone(String phone) {
        return lambdaQuery().eq(User::getPhone, phone).last("LIMIT 1").one();
    }

}
