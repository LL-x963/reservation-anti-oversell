package com.pethome.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.ServiceCategoryAddRequest;
import com.pethome.dto.ServiceCategoryUpdateRequest;
import com.pethome.entity.ServiceCategory;
import com.pethome.vo.ServiceCategorySimpleVO;
import com.pethome.vo.ServiceCategoryVO;

import java.util.List;

/**
 * 服务分类服务
 *
 * @author pet-home
 */
public interface ServiceCategoryService extends IService<ServiceCategory> {

    /**
     * 管理端：查询所有分类（含禁用），按 sort 升序
     */
    List<ServiceCategoryVO> listAllForAdmin();

    /**
     * 用户端：查询所有启用的分类，按 sort 升序
     */
    List<ServiceCategorySimpleVO> listEnabledForUser();

    /**
     * 新增分类，名称全局唯一
     *
     * @return 新增分类 id
     */
    Long add(ServiceCategoryAddRequest request);

    /**
     * 修改分类，名称全局唯一
     */
    void update(ServiceCategoryUpdateRequest request);

    /**
     * 删除分类，存在关联服务项目时禁止删除
     */
    void delete(Long id);

}
