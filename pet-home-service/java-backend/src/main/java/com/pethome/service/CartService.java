package com.pethome.service;

import com.pethome.dto.CartAddRequest;
import com.pethome.dto.CartUpdateRequest;
import com.pethome.vo.CartItemVO;

import java.util.List;

/**
 * 预约车服务（Redis 存储，key = cart:{userId}，Hash 结构：field=serviceId，value=数量）
 *
 * <p>面向用户端的方法均以 ThreadLocal 中的当前登录用户为操作对象，客户端无法越权操作他人预约车。</p>
 *
 * @author pet-home
 */
public interface CartService {

    /**
     * 添加服务到当前用户预约车：服务必须存在且已上架；已存在时数量累加
     */
    void add(CartAddRequest request);

    /**
     * 查询当前用户预约车列表（关联服务名称、价格、图片），
     * 已删除或已下架的服务会被自动清理并过滤
     */
    List<CartItemVO> list();

    /**
     * 修改当前用户预约车中某服务的数量（直接覆盖）
     */
    void update(CartUpdateRequest request);

    /**
     * 删除当前用户预约车中的指定服务
     */
    void delete(Long serviceId);

    /**
     * 清空当前用户预约车
     */
    void clean();

    /**
     * 清空指定用户的预约车
     *
     * <p>供订单模块在「下单成功」后调用，形参 userId 由调用方显式传入，
     * 不依赖 ThreadLocal，可在异步/消息消费等无请求上下文的场景使用。</p>
     */
    void clearCart(Long userId);

}
