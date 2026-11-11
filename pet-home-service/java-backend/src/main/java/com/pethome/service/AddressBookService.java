package com.pethome.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.AddressSaveRequest;
import com.pethome.entity.AddressBook;
import com.pethome.vo.AddressBookVO;

import java.util.List;

/**
 * 地址簿服务
 *
 * <p>所有操作都限定在当前登录用户范围内，越权访问其他用户地址一律按「地址不存在」处理。</p>
 *
 * @author pet-home
 */
public interface AddressBookService extends IService<AddressBook> {

    /**
     * 查询当前登录用户的地址列表（默认地址排最前）
     */
    List<AddressBookVO> listForCurrentUser();

    /**
     * 新增地址
     *
     * @return 新地址 id
     */
    Long create(AddressSaveRequest request);

    /**
     * 修改地址（只能改自己的）
     */
    void update(Long addressId, AddressSaveRequest request);

    /**
     * 删除地址（只能删自己的）；删除默认地址后自动把最近一条设为默认
     */
    void delete(Long addressId);

    /**
     * 设为默认地址
     */
    void setDefault(Long addressId);

}
