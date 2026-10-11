package com.pethome.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.entity.AddressBook;
import com.pethome.vo.AddressBookVO;

import java.util.List;

/**
 * 地址簿服务
 *
 * <p>C 端目前只提供「查询当前登录用户地址列表」（下单页选择地址用），
 * 地址的增删改后续按需补充。</p>
 *
 * @author pet-home
 */
public interface AddressBookService extends IService<AddressBook> {

    /**
     * 查询当前登录用户的地址列表（默认地址排最前）
     */
    List<AddressBookVO> listForCurrentUser();

}
