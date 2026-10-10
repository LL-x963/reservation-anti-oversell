package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;
import com.pethome.entity.User;
import com.pethome.mapper.UserMapper;
import com.pethome.service.TokenService;
import com.pethome.service.UserService;
import com.pethome.vo.LoginVO;
import com.pethome.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(String openid) {
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

}
