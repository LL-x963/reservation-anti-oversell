package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.UserLoginRequest;
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
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(UserLoginRequest request) {
        boolean hasOpenid = StringUtils.isNotBlank(request.getOpenid());
        boolean hasPhone = StringUtils.isNotBlank(request.getPhone());
        // 两种登录方式必须二选一：同时传或都不传都属于非法请求
        if (hasOpenid == hasPhone) {
            throw new BusinessException(hasOpenid
                    ? "openid 与手机号密码登录只能二选一"
                    : "请传入 openid 或 手机号+密码");
        }
        return hasOpenid
                ? loginByOpenid(request.getOpenid().trim())
                : loginByPhone(request.getPhone().trim(), request.getPassword());
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
     * 微信 openid 登录，不存在则自动注册
     */
    private LoginVO loginByOpenid(String openid) {
        User user = lambdaQuery().eq(User::getOpenid, openid).one();
        if (user == null) {
            // 首次登录自动注册
            user = new User();
            user.setOpenid(openid);
            user.setSex(0);
            save(user);
            log.info("openid 首次登录，自动注册用户: userId={}", user.getId());
        }
        return tokenService.issue(user.getId(), UserType.USER, user.getName());
    }

    /**
     * 手机号 + 密码登录，手机号未注册时自动注册（密码即注册密码）
     */
    private LoginVO loginByPhone(String phone, String password) {
        if (StringUtils.isBlank(password)) {
            throw new BusinessException("请输入密码");
        }

        User user = getByPhone(phone);
        if (user == null) {
            user = registerByPhone(phone, password);
        } else if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            // 密码错误与手机号未注册（不存在此分支）返回同一提示，避免暴露账号是否已注册
            throw new BusinessException("手机号或密码错误");
        }
        return tokenService.issue(user.getId(), UserType.USER, user.getName());
    }

    /**
     * 手机号自动注册（openid 留空，表示该账号未绑定微信）
     */
    private User registerByPhone(String phone, String password) {
        User user = new User();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setSex(0);
        try {
            save(user);
        } catch (DuplicateKeyException e) {
            // 并发场景：同一手机号被抢先注册，回查已有账号并按「已注册」处理
            log.warn("手机号并发注册，转为校验已有账号: phone={}", phone);
            User existing = getByPhone(phone);
            if (existing == null || existing.getPassword() == null
                    || !passwordEncoder.matches(password, existing.getPassword())) {
                throw new BusinessException("手机号或密码错误");
            }
            return existing;
        }
        log.info("手机号首次登录，自动注册用户: userId={}, phone={}", user.getId(), phone);
        return user;
    }

    /**
     * 按手机号查询用户（phone 为唯一索引，这里只取一条兜底历史脏数据）
     */
    private User getByPhone(String phone) {
        return lambdaQuery().eq(User::getPhone, phone).last("LIMIT 1").one();
    }

}
