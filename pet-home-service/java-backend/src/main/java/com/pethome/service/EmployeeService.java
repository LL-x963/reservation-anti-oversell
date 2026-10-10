package com.pethome.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.EmployeeLoginRequest;
import com.pethome.entity.Employee;
import com.pethome.vo.EmployeeProfileVO;
import com.pethome.vo.LoginVO;

/**
 * 管理端员工服务
 *
 * @author pet-home
 */
public interface EmployeeService extends IService<Employee> {

    /**
     * 账号密码登录
     */
    LoginVO login(EmployeeLoginRequest request);

    /**
     * 查询当前登录员工信息
     */
    EmployeeProfileVO getProfile(Long employeeId);

    /**
     * 退出登录
     */
    void logout(Long employeeId);

}
