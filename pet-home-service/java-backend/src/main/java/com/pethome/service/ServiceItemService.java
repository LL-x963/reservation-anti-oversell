package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.ServiceItemAddRequest;
import com.pethome.dto.ServiceItemPageQuery;
import com.pethome.dto.ServiceItemUpdateRequest;
import com.pethome.dto.ServiceItemUserPageQuery;
import com.pethome.entity.ServiceItem;
import com.pethome.vo.ServiceItemDetailVO;
import com.pethome.vo.ServiceItemSimpleVO;
import com.pethome.vo.ServiceItemVO;

/**
 * 服务项目服务
 *
 * @author pet-home
 */
public interface ServiceItemService extends IService<ServiceItem> {

    /**
     * 管理端：分页查询（支持分类、名称、状态筛选，含下架）
     */
    IPage<ServiceItemVO> pageForAdmin(ServiceItemPageQuery query);

    /**
     * 用户端：分页查询（仅上架，支持分类、宠物类型、价格区间筛选）
     */
    IPage<ServiceItemSimpleVO> pageForUser(ServiceItemUserPageQuery query);

    /**
     * 用户端：查询服务详情（下架不可见）
     */
    ServiceItemDetailVO detailForUser(Long id);

    /**
     * 新增服务项目，同一分类内名称唯一
     *
     * @return 新增服务项目 id
     */
    Long add(ServiceItemAddRequest request);

    /**
     * 修改服务项目，同一分类内名称唯一
     */
    void update(ServiceItemUpdateRequest request);

    /**
     * 服务项目上下架
     *
     * @param status 1上架 0下架
     */
    void updateStatus(Long id, Integer status);

    /**
     * 删除服务项目，存在未完成订单时禁止删除
     */
    void delete(Long id);

}
