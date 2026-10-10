package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.ServiceCategoryAddRequest;
import com.pethome.dto.ServiceCategoryUpdateRequest;
import com.pethome.entity.ServiceCategory;
import com.pethome.mapper.ServiceCategoryMapper;
import com.pethome.mapper.ServiceItemMapper;
import com.pethome.service.ServiceCategoryService;
import com.pethome.vo.ServiceCategorySimpleVO;
import com.pethome.vo.ServiceCategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 服务分类服务实现
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCategoryServiceImpl extends ServiceImpl<ServiceCategoryMapper, ServiceCategory>
        implements ServiceCategoryService {

    /** 启用 */
    private static final int STATUS_ENABLED = 1;
    /** 默认状态：启用 */
    private static final int DEFAULT_STATUS = STATUS_ENABLED;
    /** 默认排序值 */
    private static final int DEFAULT_SORT = 0;

    private final ServiceItemMapper serviceItemMapper;

    @Override
    public List<ServiceCategoryVO> listAllForAdmin() {
        return lambdaQuery()
                .orderByAsc(ServiceCategory::getSort)
                .orderByAsc(ServiceCategory::getId)
                .list()
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ServiceCategorySimpleVO> listEnabledForUser() {
        return lambdaQuery()
                .eq(ServiceCategory::getStatus, STATUS_ENABLED)
                .orderByAsc(ServiceCategory::getSort)
                .orderByAsc(ServiceCategory::getId)
                .list()
                .stream()
                .map(category -> {
                    ServiceCategorySimpleVO vo = new ServiceCategorySimpleVO();
                    BeanUtils.copyProperties(category, vo);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(ServiceCategoryAddRequest request) {
        String name = request.getName().trim();
        checkNameUnique(name, null);

        ServiceCategory entity = new ServiceCategory();
        entity.setName(name);
        entity.setSort(request.getSort() == null ? DEFAULT_SORT : request.getSort());
        entity.setStatus(request.getStatus() == null ? DEFAULT_STATUS : request.getStatus());
        save(entity);

        log.info("新增服务分类成功: id={}, name={}", entity.getId(), name);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ServiceCategoryUpdateRequest request) {
        if (getById(request.getId()) == null) {
            throw new BusinessException("分类不存在");
        }

        String name = request.getName().trim();
        checkNameUnique(name, request.getId());

        ServiceCategory entity = new ServiceCategory();
        entity.setId(request.getId());
        entity.setName(name);
        entity.setSort(request.getSort());
        entity.setStatus(request.getStatus());
        updateById(entity);

        log.info("修改服务分类成功: id={}, name={}", request.getId(), name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("分类不存在");
        }

        long itemCount = serviceItemMapper.countByCategoryId(id);
        if (itemCount > 0) {
            throw new BusinessException("该分类下存在 " + itemCount + " 个服务项目，请先删除或转移后再删除分类");
        }

        removeById(id);
        log.info("删除服务分类成功: id={}", id);
    }

    /**
     * 校验分类名称全局唯一（含已禁用分类）
     *
     * @param name      分类名称
     * @param excludeId 需要排除的分类 id（修改时排除自身），新增传 null
     */
    private void checkNameUnique(String name, Long excludeId) {
        Long count = lambdaQuery()
                .eq(ServiceCategory::getName, name)
                .ne(excludeId != null, ServiceCategory::getId, excludeId)
                .count();
        if (count != null && count > 0) {
            throw new BusinessException("分类名称已存在：" + name);
        }
    }

    private ServiceCategoryVO toVO(ServiceCategory category) {
        ServiceCategoryVO vo = new ServiceCategoryVO();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }

}
