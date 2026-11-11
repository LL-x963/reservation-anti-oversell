package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.context.UserContext;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.AddressSaveRequest;
import com.pethome.entity.AddressBook;
import com.pethome.mapper.AddressBookMapper;
import com.pethome.service.AddressBookService;
import com.pethome.util.AddressUtil;
import com.pethome.vo.AddressBookVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 地址簿服务实现
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressBookServiceImpl extends ServiceImpl<AddressBookMapper, AddressBook> implements AddressBookService {

    /** 单用户地址数量上限，避免恶意刷数据 */
    private static final int MAX_ADDRESS_COUNT = 20;

    private static final int DEFAULT_YES = 1;

    private static final int DEFAULT_NO = 0;

    @Override
    public List<AddressBookVO> listForCurrentUser() {
        Long userId = UserContext.requireUserId();
        return lambdaQuery()
                .eq(AddressBook::getUserId, userId)
                // 默认地址排最前，其余按新增先后倒序
                .orderByDesc(AddressBook::getIsDefault)
                .orderByDesc(AddressBook::getId)
                .list()
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AddressSaveRequest request) {
        Long userId = UserContext.requireUserId();
        long count = lambdaQuery().eq(AddressBook::getUserId, userId).count();
        if (count >= MAX_ADDRESS_COUNT) {
            throw new BusinessException("最多只能保存 " + MAX_ADDRESS_COUNT + " 个地址");
        }

        // 第一条地址自动设为默认
        boolean setDefault = Boolean.TRUE.equals(request.getIsDefault()) || count == 0;

        AddressBook address = new AddressBook();
        fill(address, request);
        address.setUserId(userId);
        address.setIsDefault(setDefault ? DEFAULT_YES : DEFAULT_NO);
        save(address);

        if (setDefault) {
            clearOtherDefault(userId, address.getId());
        }
        log.info("新增地址成功: userId={}, addressId={}", userId, address.getId());
        return address.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long addressId, AddressSaveRequest request) {
        Long userId = UserContext.requireUserId();
        AddressBook address = getOwned(addressId, userId);

        fill(address, request);
        boolean setDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (setDefault) {
            address.setIsDefault(DEFAULT_YES);
        }
        updateById(address);

        if (setDefault) {
            clearOtherDefault(userId, addressId);
        }
        log.info("修改地址成功: userId={}, addressId={}", userId, addressId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long addressId) {
        Long userId = UserContext.requireUserId();
        AddressBook address = getOwned(addressId, userId);
        removeById(addressId);

        // 删除的是默认地址：把最近新增的一条补为默认，避免下单页没有默认地址
        if (Integer.valueOf(DEFAULT_YES).equals(address.getIsDefault())) {
            AddressBook next = lambdaQuery()
                    .eq(AddressBook::getUserId, userId)
                    .orderByDesc(AddressBook::getId)
                    .last("LIMIT 1")
                    .one();
            if (next != null) {
                next.setIsDefault(DEFAULT_YES);
                updateById(next);
            }
        }
        log.info("删除地址成功: userId={}, addressId={}", userId, addressId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long addressId) {
        Long userId = UserContext.requireUserId();
        AddressBook address = getOwned(addressId, userId);
        address.setIsDefault(DEFAULT_YES);
        updateById(address);
        clearOtherDefault(userId, addressId);
        log.info("设置默认地址成功: userId={}, addressId={}", userId, addressId);
    }

    /**
     * 校验地址归属，越权与不存在返回同一提示，避免探测他人地址 id
     */
    private AddressBook getOwned(Long addressId, Long userId) {
        AddressBook address = getById(addressId);
        if (address == null || !Objects.equals(address.getUserId(), userId)) {
            throw new BusinessException("地址不存在");
        }
        return address;
    }

    private void fill(AddressBook address, AddressSaveRequest request) {
        address.setConsignee(request.getConsignee().trim());
        address.setPhone(request.getPhone().trim());
        address.setSex(request.getSex() == null ? 0 : request.getSex());
        address.setProvinceName(trimToNull(request.getProvinceName()));
        address.setCityName(trimToNull(request.getCityName()));
        address.setDistrictName(trimToNull(request.getDistrictName()));
        address.setDetail(request.getDetail().trim());
        address.setLabel(trimToNull(request.getLabel()));
    }

    private void clearOtherDefault(Long userId, Long keepId) {
        lambdaUpdate()
                .eq(AddressBook::getUserId, userId)
                .ne(AddressBook::getId, keepId)
                .eq(AddressBook::getIsDefault, DEFAULT_YES)
                .set(AddressBook::getIsDefault, DEFAULT_NO)
                .update();
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private AddressBookVO toVO(AddressBook address) {
        AddressBookVO vo = new AddressBookVO();
        vo.setId(address.getId());
        vo.setConsignee(address.getConsignee());
        vo.setPhone(address.getPhone());
        vo.setLabel(address.getLabel());
        vo.setIsDefault(address.getIsDefault());
        vo.setFullAddress(AddressUtil.fullAddress(address));
        // 原始字段用于编辑回填
        vo.setProvinceName(address.getProvinceName());
        vo.setCityName(address.getCityName());
        vo.setDistrictName(address.getDistrictName());
        vo.setDetail(address.getDetail());
        return vo;
    }

}
