package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;
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

    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public LoginVO login(EmployeeLoginRequest request) {
        Employee employee = lambdaQuery().eq(Employee::getUsername, request.getUsername()).one();
        // 用户不存在与密码错误返回同一提示，避免暴露账号是否存在
        if (employee == null || employee.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(employee.getStatus())) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        return tokenService.issue(employee.getId(), UserType.EMPLOYEE);
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
