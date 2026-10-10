package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.context.UserContext;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.PetProfileAddRequest;
import com.pethome.dto.PetProfileUpdateRequest;
import com.pethome.entity.PetProfile;
import com.pethome.mapper.PetProfileMapper;
import com.pethome.service.PetProfileService;
import com.pethome.vo.PetProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 宠物档案服务实现
 *
 * <p>所有操作的归属用户均取自 ThreadLocal（{@link UserContext}），客户端无法越权指定 user_id。</p>
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetProfileServiceImpl extends ServiceImpl<PetProfileMapper, PetProfile> implements PetProfileService {

    @Override
    public List<PetProfileVO> listForCurrentUser() {
        Long userId = UserContext.requireUserId();
        return lambdaQuery()
                .eq(PetProfile::getUserId, userId)
                .orderByAsc(PetProfile::getId)
                .list()
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public PetProfileVO detailForCurrentUser(Long id) {
        return toVO(getOwnedPet(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(PetProfileAddRequest request) {
        Long userId = UserContext.requireUserId();
        String name = request.getName().trim();
        checkNameUnique(userId, name, null);

        PetProfile entity = new PetProfile();
        BeanUtils.copyProperties(request, entity);
        entity.setName(name);
        // 自动绑定当前登录用户
        entity.setUserId(userId);
        save(entity);

        log.info("新增宠物成功: id={}, userId={}, name={}", entity.getId(), userId, name);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(PetProfileUpdateRequest request) {
        Long userId = UserContext.requireUserId();
        getOwnedPet(request.getId());

        String name = request.getName().trim();
        checkNameUnique(userId, name, request.getId());

        PetProfile entity = new PetProfile();
        BeanUtils.copyProperties(request, entity);
        entity.setName(name);
        // 归属用户始终以登录态为准，防止越权改归属
        entity.setUserId(userId);
        updateById(entity);

        log.info("修改宠物成功: id={}, userId={}, name={}", request.getId(), userId, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getOwnedPet(id);

        long unfinishedOrders = baseMapper.countUnfinishedOrderByPetId(id);
        if (unfinishedOrders > 0) {
            throw new BusinessException("该宠物存在 " + unfinishedOrders + " 个未完成订单，暂不能删除");
        }

        removeById(id);
        log.info("删除宠物成功: id={}, userId={}", id, UserContext.getUserId());
    }

    /**
     * 查询当前登录用户名下的宠物，不存在或不属于当前用户统一按「宠物不存在」处理，避免越权探测
     */
    private PetProfile getOwnedPet(Long id) {
        Long userId = UserContext.requireUserId();
        PetProfile pet = lambdaQuery()
                .eq(PetProfile::getId, id)
                .eq(PetProfile::getUserId, userId)
                .one();
        if (pet == null) {
            throw new BusinessException("宠物不存在");
        }
        return pet;
    }

    /**
     * 校验同一用户下宠物名称唯一
     *
     * @param userId    归属用户 id
     * @param name      宠物名称
     * @param excludeId 需要排除的宠物 id（修改时排除自身），新增传 null
     */
    private void checkNameUnique(Long userId, String name, Long excludeId) {
        Long count = lambdaQuery()
                .eq(PetProfile::getUserId, userId)
                .eq(PetProfile::getName, name)
                .ne(excludeId != null, PetProfile::getId, excludeId)
                .count();
        if (count != null && count > 0) {
            throw new BusinessException("宠物名称已存在：" + name);
        }
    }

    private PetProfileVO toVO(PetProfile pet) {
        PetProfileVO vo = new PetProfileVO();
        BeanUtils.copyProperties(pet, vo);
        return vo;
    }

}
