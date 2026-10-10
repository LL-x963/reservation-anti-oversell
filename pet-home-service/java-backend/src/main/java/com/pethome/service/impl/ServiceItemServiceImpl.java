package com.pethome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.ServiceItemAddRequest;
import com.pethome.dto.ServiceItemPageQuery;
import com.pethome.dto.ServiceItemUpdateRequest;
import com.pethome.dto.ServiceItemUserPageQuery;
import com.pethome.entity.ServiceItem;
import com.pethome.mapper.ServiceItemMapper;
import com.pethome.service.ServiceCategoryService;
import com.pethome.service.ServiceItemService;
import com.pethome.vo.ServiceItemDetailVO;
import com.pethome.vo.ServiceItemSimpleVO;
import com.pethome.vo.ServiceItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 服务项目服务实现
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceItemServiceImpl extends ServiceImpl<ServiceItemMapper, ServiceItem> implements ServiceItemService {

    /** 上架 */
    private static final int STATUS_ON_SALE = 1;
    /** 默认状态：上架 */
    private static final int DEFAULT_STATUS = STATUS_ON_SALE;

    private final ServiceCategoryService serviceCategoryService;

    @Override
    public IPage<ServiceItemVO> pageForAdmin(ServiceItemPageQuery query) {
        LambdaQueryWrapper<ServiceItem> wrapper = new LambdaQueryWrapper<ServiceItem>()
                .eq(query.getCategoryId() != null, ServiceItem::getCategoryId, query.getCategoryId())
                .like(StringUtils.isNotBlank(query.getName()), ServiceItem::getName, query.getName())
                .eq(query.getStatus() != null, ServiceItem::getStatus, query.getStatus())
                .orderByDesc(ServiceItem::getId);

        Page<ServiceItem> page = page(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return page.convert(this::toVO);
    }

    @Override
    public IPage<ServiceItemSimpleVO> pageForUser(ServiceItemUserPageQuery query) {
        if (query.getMinPrice() != null && query.getMaxPrice() != null
                && query.getMinPrice().compareTo(query.getMaxPrice()) > 0) {
            throw new BusinessException("最低价格不能大于最高价格");
        }

        LambdaQueryWrapper<ServiceItem> wrapper = new LambdaQueryWrapper<ServiceItem>()
                // 用户端只查上架
                .eq(ServiceItem::getStatus, STATUS_ON_SALE)
                .eq(query.getCategoryId() != null, ServiceItem::getCategoryId, query.getCategoryId())
                .eq(StringUtils.isNotBlank(query.getPetType()), ServiceItem::getPetType, query.getPetType())
                .ge(query.getMinPrice() != null, ServiceItem::getPrice, query.getMinPrice())
                .le(query.getMaxPrice() != null, ServiceItem::getPrice, query.getMaxPrice())
                .orderByAsc(ServiceItem::getId);

        Page<ServiceItem> page = page(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return page.convert(this::toSimpleVO);
    }

    @Override
    public ServiceItemDetailVO detailForUser(Long id) {
        ServiceItem item = getById(id);
        if (item == null || !Integer.valueOf(STATUS_ON_SALE).equals(item.getStatus())) {
            throw new BusinessException("服务不存在或已下架");
        }
        ServiceItemDetailVO detailVO = new ServiceItemDetailVO();
        BeanUtils.copyProperties(item, detailVO);
        return detailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(ServiceItemAddRequest request) {
        checkCategoryExists(request.getCategoryId());
        String name = request.getName().trim();
        checkNameUnique(request.getCategoryId(), name, null);

        ServiceItem entity = new ServiceItem();
        BeanUtils.copyProperties(request, entity);
        entity.setName(name);
        entity.setStatus(request.getStatus() == null ? DEFAULT_STATUS : request.getStatus());
        save(entity);

        log.info("新增服务项目成功: id={}, categoryId={}, name={}", entity.getId(), entity.getCategoryId(), name);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ServiceItemUpdateRequest request) {
        if (getById(request.getId()) == null) {
            throw new BusinessException("服务项目不存在");
        }
        checkCategoryExists(request.getCategoryId());
        String name = request.getName().trim();
        checkNameUnique(request.getCategoryId(), name, request.getId());

        ServiceItem entity = new ServiceItem();
        BeanUtils.copyProperties(request, entity);
        entity.setName(name);
        // 上下架状态不在此接口修改（请求体无 status，null 字段不会被更新）
        updateById(entity);

        log.info("修改服务项目成功: id={}, name={}", request.getId(), name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (getById(id) == null) {
            throw new BusinessException("服务项目不存在");
        }

        ServiceItem entity = new ServiceItem();
        entity.setId(id);
        entity.setStatus(status);
        updateById(entity);

        log.info("服务项目上下架成功: id={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("服务项目不存在");
        }

        long unfinishedOrders = baseMapper.countUnfinishedOrderByServiceId(id);
        if (unfinishedOrders > 0) {
            throw new BusinessException("该服务存在 " + unfinishedOrders + " 个未完成订单，暂不能删除");
        }

        removeById(id);
        log.info("删除服务项目成功: id={}", id);
    }

    private void checkCategoryExists(Long categoryId) {
        if (serviceCategoryService.getById(categoryId) == null) {
            throw new BusinessException("所属分类不存在");
        }
    }

    /**
     * 校验同一分类下服务名称唯一
     *
     * @param categoryId 分类 id
     * @param name       服务名称
     * @param excludeId  需要排除的服务项目 id（修改时排除自身），新增传 null
     */
    private void checkNameUnique(Long categoryId, String name, Long excludeId) {
        Long count = lambdaQuery()
                .eq(ServiceItem::getCategoryId, categoryId)
                .eq(ServiceItem::getName, name)
                .ne(excludeId != null, ServiceItem::getId, excludeId)
                .count();
        if (count != null && count > 0) {
            throw new BusinessException("该分类下服务名称已存在：" + name);
        }
    }

    private ServiceItemVO toVO(ServiceItem item) {
        ServiceItemVO vo = new ServiceItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    private ServiceItemSimpleVO toSimpleVO(ServiceItem item) {
        ServiceItemSimpleVO vo = new ServiceItemSimpleVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

}
