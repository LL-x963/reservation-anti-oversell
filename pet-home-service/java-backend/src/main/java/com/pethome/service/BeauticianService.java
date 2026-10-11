package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.BeauticianAddRequest;
import com.pethome.dto.BeauticianPageQuery;
import com.pethome.dto.BeauticianUpdateRequest;
import com.pethome.entity.Beautician;
import com.pethome.vo.BeauticianSimpleVO;
import com.pethome.vo.BeauticianVO;

import java.util.List;

/**
 * 美容师服务（管理端）
 *
 * @author pet-home
 */
public interface BeauticianService extends IService<Beautician> {

    /**
     * 分页查询，支持按姓名、技能标签、状态筛选
     */
    IPage<BeauticianVO> pageForAdmin(BeauticianPageQuery query);

    /**
     * 查询所有在职（status=1）美容师列表
     */
    List<BeauticianVO> listOnDuty();

    /**
     * 用户端：在职美容师列表（仅返回选择所需字段：id / 姓名 / 头像 / 评分）
     */
    List<BeauticianSimpleVO> listOnDutyForUser();

    /**
     * 新增美容师，评分默认 5.00，状态默认在职
     *
     * @return 新增美容师 id
     */
    Long add(BeauticianAddRequest request);

    /**
     * 修改美容师信息（不包含评分）
     */
    void update(BeauticianUpdateRequest request);

    /**
     * 删除美容师，存在未完成订单时禁止删除
     */
    void delete(Long id);

}
