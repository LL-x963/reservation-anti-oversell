package com.pethome.service.impl;

import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.context.UserContext;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.CartAddRequest;
import com.pethome.dto.CartUpdateRequest;
import com.pethome.entity.ServiceItem;
import com.pethome.service.CartService;
import com.pethome.service.ServiceItemService;
import com.pethome.util.RedisUtil;
import com.pethome.vo.CartItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预约车服务实现（Redis Hash 存储，不落库）
 *
 * <p>存储结构：key = {@code cart:{userId}}，field = serviceId，value = 数量；TTL 7 天，每次操作刷新。</p>
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    /** 服务上架 */
    private static final int STATUS_ON_SALE = 1;
    /** 单件服务最小数量 */
    private static final int MIN_QUANTITY = 1;
    /** 单件服务最大数量 */
    private static final int MAX_QUANTITY = 99;
    /** 预约车过期天数 */
    private static final long CART_EXPIRE_DAYS = 7L;

    private final RedisUtil redisUtil;
    private final ServiceItemService serviceItemService;

    @Override
    public void add(CartAddRequest request) {
        Long userId = UserContext.requireUserId();
        checkQuantity(request.getQuantity());
        // 添加时校验服务是否存在、是否上架
        getOnSaleService(request.getServiceId());

        String key = RedisKeyConstant.cartKey(userId);
        String field = String.valueOf(request.getServiceId());
        // 原子累加并校验上限：读-改-写在并发下会丢更新，这里交给 Redis 原子执行
        long target = redisUtil.hIncrementWithLimit(key, field, request.getQuantity(), MAX_QUANTITY);
        if (target < 0) {
            throw new BusinessException("单个服务数量不能超过" + MAX_QUANTITY);
        }

        refreshExpire(key);
        log.info("加入预约车成功: userId={}, serviceId={}, quantity={}", userId, request.getServiceId(), target);
    }

    @Override
    public List<CartItemVO> list() {
        Long userId = UserContext.requireUserId();
        String key = RedisKeyConstant.cartKey(userId);
        Map<Object, Object> entries = redisUtil.hGetAllString(key);
        if (CollectionUtils.isEmpty(entries)) {
            return Collections.emptyList();
        }

        // 关联数据库查询服务名称、价格、图片
        List<Long> serviceIds = entries.keySet().stream()
                .map(this::toServiceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, ServiceItem> itemMap = serviceIds.isEmpty() ? Collections.emptyMap()
                : serviceItemService.listByIds(serviceIds).stream()
                .collect(Collectors.toMap(ServiceItem::getId, Function.identity(), (a, b) -> a));

        List<CartItemVO> items = new ArrayList<>(entries.size());
        List<Object> invalidFields = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Long serviceId = toServiceId(entry.getKey());
            Integer quantity = toQuantity(entry.getValue());
            ServiceItem item = serviceId == null ? null : itemMap.get(serviceId);
            // 服务已删除 / 已下架 / 数据脏：自动清理并过滤
            if (serviceId == null || quantity == null || item == null
                    || !Integer.valueOf(STATUS_ON_SALE).equals(item.getStatus())) {
                invalidFields.add(entry.getKey());
                continue;
            }
            items.add(buildItemVO(item, quantity));
        }

        if (!invalidFields.isEmpty()) {
            redisUtil.hDeleteString(key, invalidFields.stream()
                    .map(String::valueOf)
                    .toArray(String[]::new));
            log.info("预约车自动清理失效服务: userId={}, fields={}", userId, invalidFields);
        }
        refreshExpire(key);

        items.sort(Comparator.comparing(CartItemVO::getServiceId));
        return items;
    }

    @Override
    public void update(CartUpdateRequest request) {
        Long userId = UserContext.requireUserId();
        checkQuantity(request.getQuantity());

        String key = RedisKeyConstant.cartKey(userId);
        String field = String.valueOf(request.getServiceId());
        if (!redisUtil.hHasKeyString(key, field)) {
            throw new BusinessException("预约车中不存在该服务");
        }

        // 直接覆盖为传入数量
        redisUtil.hSetString(key, field, String.valueOf(request.getQuantity()));
        refreshExpire(key);
        log.info("修改预约车数量成功: userId={}, serviceId={}, quantity={}",
                userId, request.getServiceId(), request.getQuantity());
    }

    @Override
    public void delete(Long serviceId) {
        Long userId = UserContext.requireUserId();
        String key = RedisKeyConstant.cartKey(userId);
        String field = String.valueOf(serviceId);
        if (!redisUtil.hHasKeyString(key, field)) {
            throw new BusinessException("预约车中不存在该服务");
        }

        redisUtil.hDeleteString(key, field);
        refreshExpire(key);
        log.info("删除预约车服务成功: userId={}, serviceId={}", userId, serviceId);
    }

    @Override
    public void clean() {
        Long userId = UserContext.requireUserId();
        redisUtil.delete(RedisKeyConstant.cartKey(userId));
        log.info("清空预约车成功: userId={}", userId);
    }

    @Override
    public void clearCart(Long userId) {
        if (userId == null) {
            return;
        }
        redisUtil.delete(RedisKeyConstant.cartKey(userId));
        log.info("下单成功后清空预约车: userId={}", userId);
    }

    /**
     * 刷新预约车 TTL（key 不存在时无需操作）
     */
    private void refreshExpire(String key) {
        if (redisUtil.hasKey(key)) {
            redisUtil.expire(key, CART_EXPIRE_DAYS, TimeUnit.DAYS);
        }
    }

    private void checkQuantity(Integer quantity) {
        if (quantity == null || quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new BusinessException("数量必须在 " + MIN_QUANTITY + " ~ " + MAX_QUANTITY + " 之间");
        }
    }

    private ServiceItem getOnSaleService(Long serviceId) {
        ServiceItem item = serviceItemService.getById(serviceId);
        if (item == null) {
            throw new BusinessException("服务不存在");
        }
        if (!Integer.valueOf(STATUS_ON_SALE).equals(item.getStatus())) {
            throw new BusinessException("服务已下架");
        }
        return item;
    }

    private int currentQuantity(String key, String field) {
        Integer quantity = toQuantity(redisUtil.hGet(key, field));
        return quantity == null ? 0 : quantity;
    }

    private CartItemVO buildItemVO(ServiceItem item, Integer quantity) {
        CartItemVO vo = new CartItemVO();
        vo.setServiceId(item.getId());
        vo.setName(item.getName());
        vo.setPrice(item.getPrice());
        vo.setImage(item.getImage());
        vo.setQuantity(quantity);
        vo.setAmount(item.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return vo;
    }

    private Integer toQuantity(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            log.warn("预约车数量格式非法: {}", value);
            return null;
        }
    }

    private Long toServiceId(Object field) {
        if (field == null) {
            return null;
        }
        if (field instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(field).trim());
        } catch (NumberFormatException e) {
            log.warn("预约车服务id格式非法: {}", field);
            return null;
        }
    }

}
