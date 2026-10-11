package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.context.UserContext;
import com.pethome.entity.AddressBook;
import com.pethome.mapper.AddressBookMapper;
import com.pethome.service.AddressBookService;
import com.pethome.util.AddressUtil;
import com.pethome.vo.AddressBookVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

    private AddressBookVO toVO(AddressBook address) {
        AddressBookVO vo = new AddressBookVO();
        vo.setId(address.getId());
        vo.setConsignee(address.getConsignee());
        vo.setPhone(address.getPhone());
        vo.setLabel(address.getLabel());
        vo.setIsDefault(address.getIsDefault());
        vo.setFullAddress(AddressUtil.fullAddress(address));
        return vo;
    }

}
