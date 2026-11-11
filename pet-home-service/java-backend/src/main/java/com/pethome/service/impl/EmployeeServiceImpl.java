package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.enums.EmployeeRole;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;
import com.pethome.common.security.LoginGuard;
import com.pethome.dto.EmployeeLoginRequest;
import com.pethome.entity.Employee;
import com.pethome.mapper.EmployeeMapper;
import com.pethome.service.EmployeeService;
import com.pethome.service.TokenService;
import com.pethome.vo.EmployeeProfileVO;
import com.pethome.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 管理端员工服务实现
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    private static final int STATUS_ENABLED = 1;

    private static final String LOGIN_ACCOUNT_PREFIX = "employee:";

    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginGuard loginGuard;

    @Override
    public LoginVO login(EmployeeLoginRequest request) {
        String accountKey = LOGIN_ACCOUNT_PREFIX + request.getUsername();
        loginGuard.checkAccountLocked(accountKey);

        Employee employee = lambdaQuery().eq(Employee::getUsername, request.getUsername()).one();
        // 用户不存在与密码错误返回同一提示，避免暴露账号是否存在
        if (employee == null || employee.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            loginGuard.onLoginFailure(accountKey);
            throw new BusinessException("用户名或密码错误");
        }
        // 账号被禁用属于凭据正确但无权限，不计入失败次数，避免把管理员锁死
        if (!Integer.valueOf(STATUS_ENABLED).equals(employee.getStatus())) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        loginGuard.onLoginSuccess(accountKey);
        // 角色随 token 下发；库中为空时按最小权限 OPERATOR 处理
        EmployeeRole role = EmployeeRole.fromCode(employee.getRole());
        return tokenService.issue(employee.getId(), UserType.EMPLOYEE, employee.getName(), role.name());
    }

    @Override
    public EmployeeProfileVO getProfile(Long employeeId) {
        Employee employee = getById(employeeId);
        if (employee == null) {
            throw new BusinessException("员工不存在");
        }
        EmployeeProfileVO profileVO = new EmployeeProfileVO();
        BeanUtils.copyProperties(employee, profileVO);
        return profileVO;
    }

    @Override
    public void logout(Long employeeId) {
        tokenService.revoke(employeeId, UserType.EMPLOYEE);
    }

}
