package com.pethome.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.PetProfileAddRequest;
import com.pethome.dto.PetProfileUpdateRequest;
import com.pethome.entity.PetProfile;
import com.pethome.vo.PetProfileVO;

import java.util.List;

/**
 * 宠物档案服务（仅用户端，所有操作限定在当前登录用户范围内）
 *
 * @author pet-home
 */
public interface PetProfileService extends IService<PetProfile> {

    /**
     * 查询当前登录用户的全部宠物
     */
    List<PetProfileVO> listForCurrentUser();

    /**
     * 查询当前登录用户的某只宠物详情
     */
    PetProfileVO detailForCurrentUser(Long id);

    /**
     * 新增宠物，自动绑定当前登录用户，同一用户下名称唯一
     *
     * @return 新增宠物 id
     */
    Long add(PetProfileAddRequest request);

    /**
     * 修改宠物，仅允许修改当前登录用户的宠物，同一用户下名称唯一
     */
    void update(PetProfileUpdateRequest request);

    /**
     * 删除宠物，仅允许删除当前登录用户的宠物，存在未完成订单时禁止删除
     */
    void delete(Long id);

}
