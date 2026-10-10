package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.ServiceCategoryAddRequest;
import com.pethome.dto.ServiceItemAddRequest;
import com.pethome.dto.ServiceItemPageQuery;
import com.pethome.dto.ServiceItemUpdateRequest;
import com.pethome.dto.ServiceItemUserPageQuery;
import com.pethome.entity.ServiceItem;
import com.pethome.vo.ServiceItemDetailVO;
import com.pethome.vo.ServiceItemSimpleVO;
import com.pethome.vo.ServiceItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 服务项目服务层测试（覆盖业务规则，事务回滚不污染数据库）
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class ServiceItemServiceTest {

    private static final String CAT_A = "IT-服务分类A";
    private static final String CAT_B = "IT-服务分类B";
    private static final String ITEM_NAME = "IT-猫咪洗澡";
    private static final String ITEM_NAME_2 = "IT-狗狗美容";

    @Autowired
    private ServiceItemService serviceItemService;

    @Autowired
    private ServiceCategoryService serviceCategoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long catAId;
    private Long catBId;

    @BeforeEach
    void setUp() {
        catAId = addCategory(CAT_A);
        catBId = addCategory(CAT_B);
    }

    // ==================== 新增 ====================

    @Test
    void addShouldFillDefaultsAndAuditFields() {
        Long id = addItem(catAId, ITEM_NAME, "99.00", "猫", null, 1);

        ServiceItem entity = serviceItemService.getById(id);
        assertThat(entity).isNotNull();
        assertThat(entity.getCategoryId()).isEqualTo(catAId);
        assertThat(entity.getName()).isEqualTo(ITEM_NAME);
        assertThat(entity.getPrice()).isEqualByComparingTo("99.00");
        assertThat(entity.getStatus()).isEqualTo(1);
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void addShouldTrimName() {
        Long id = addItem(catAId, "   " + ITEM_NAME + "   ", "99.00", "猫", null, 1);

        assertThat(serviceItemService.getById(id).getName()).isEqualTo(ITEM_NAME);
    }

    @Test
    void addShouldRejectNonexistentCategory() {
        assertThatThrownBy(() -> addItem(99999999L, ITEM_NAME, "99.00", "猫", null, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所属分类不存在");
    }

    @Test
    void addShouldRejectDuplicatedNameInSameCategory() {
        addItem(catAId, ITEM_NAME, "99.00", "猫", null, 1);

        assertThatThrownBy(() -> addItem(catAId, ITEM_NAME, "199.00", "狗", null, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该分类下服务名称已存在");
    }

    @Test
    void addShouldAllowSameNameInDifferentCategory() {
        Long first = addItem(catAId, ITEM_NAME, "99.00", "猫", null, 1);
        Long second = addItem(catBId, ITEM_NAME, "199.00", "狗", null, 1);

        assertThat(first).isNotEqualTo(second);
    }

    // ==================== 修改 ====================

    @Test
    void updateShouldSucceedAndKeepStatus() {
        Long id = addItem(catAId, ITEM_NAME, "99.00", "猫", null, 1);
        // 先下架
        serviceItemService.updateStatus(id, 0);

        ServiceItemUpdateRequest request = new ServiceItemUpdateRequest();
        request.setId(id);
        request.setCategoryId(catAId);
        request.setName(ITEM_NAME + "改");
        request.setPrice(new BigDecimal("128.00"));
        request.setServiceDuration(90);
        request.setPetType("通用");
        serviceItemService.update(request);

        ServiceItem entity = serviceItemService.getById(id);
        assertThat(entity.getName()).isEqualTo(ITEM_NAME + "改");
        assertThat(entity.getPrice()).isEqualByComparingTo("128.00");
        assertThat(entity.getServiceDuration()).isEqualTo(90);
        // 修改接口不影响上下架状态
        assertThat(entity.getStatus()).isZero();
    }

    @Test
    void updateShouldRejectDuplicatedNameInSameCategory() {
        Long id = addItem(catAId, ITEM_NAME, "99.00", "猫", null, 1);
        addItem(catAId, ITEM_NAME_2, "199.00", "狗", null, 1);

        ServiceItemUpdateRequest request = new ServiceItemUpdateRequest();
        request.setId(id);
        request.setCategoryId(catAId);
        request.setName(ITEM_NAME_2);
        request.setPrice(new BigDecimal("99.00"));

        assertThatThrownBy(() -> serviceItemService.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该分类下服务名称已存在");
    }

    @Test
    void updateShouldAllowKeepingOwnName() {
        Long id = addItem(catAId, ITEM_NAME, "99.00", "猫", null, 1);

        ServiceItemUpdateRequest request = new ServiceItemUpdateRequest();
        request.setId(id);
        request.setCategoryId(catAId);
        request.setName(ITEM_NAME);
        request.setPrice(new BigDecimal("88.00"));

        serviceItemService.update(request);

        assertThat(serviceItemService.getById(id).getPrice()).isEqualByComparingTo("88.00");
    }

    @Test
    void updateNonexistentItemShouldFail() {
        ServiceItemUpdateRequest request = new ServiceItemUpdateRequest();
        request.setId(99999999L);
        request.setCategoryId(catAId);
        request.setName(ITEM_NAME);
        request.setPrice(new BigDecimal("99.00"));

        assertThatThrownBy(() -> serviceItemService.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务项目不存在");
    }

    // ==================== 上下架 ====================

    @Test
    void updateStatusShouldWork() {
        Long id = addItem(catAId, ITEM_NAME, "99.00", "猫", null, 1);

        serviceItemService.updateStatus(id, 0);
        assertThat(serviceItemService.getById(id).getStatus()).isZero();

        serviceItemService.updateStatus(id, 1);
        assertThat(serviceItemService.getById(id).getStatus()).isEqualTo(1);
    }

    @Test
    void updateStatusNonexistentItemShouldFail() {
        assertThatThrownBy(() -> serviceItemService.updateStatus(99999999L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务项目不存在");
    }

    // ==================== 删除 ====================

    @Test
    void deleteShouldSucceedWhenNoOrderRelated() {
        Long id = addItem(catAId, ITEM_NAME, "99.00", "猫", null, 1);

        serviceItemService.delete(id);

        assertThat(serviceItemService.getById(id)).isNull();
    }

    @Test
    void deleteShouldRejectWhenUnfinishedOrderExists() {
        for (int orderStatus : new int[]{0, 1, 2, 3}) {
            Long id = addItem(catAId, ITEM_NAME + orderStatus, "99.00", "猫", null, 1);
            insertOrderDetail(id, orderStatus);

            assertThatThrownBy(() -> serviceItemService.delete(id))
                    .as("订单状态 %s 属于未完成", orderStatus)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未完成订单");
        }
    }

    @Test
    void deleteShouldAllowWhenOnlyFinishedOrderExists() {
        for (int orderStatus : new int[]{4, 5, 6}) {
            Long id = addItem(catAId, ITEM_NAME + orderStatus, "99.00", "猫", null, 1);
            insertOrderDetail(id, orderStatus);

            serviceItemService.delete(id);

            assertThat(serviceItemService.getById(id)).isNull();
        }
    }

    @Test
    void deleteNonexistentItemShouldFail() {
        assertThatThrownBy(() -> serviceItemService.delete(99999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务项目不存在");
    }

    // ==================== 分页查询 ====================

    @Test
    void pageForAdminShouldSupportCategoryNameAndStatusFilter() {
        addItem(catAId, ITEM_NAME, "99.00", "猫", null, 1);
        addItem(catAId, ITEM_NAME_2, "199.00", "狗", null, 0);
        addItem(catBId, ITEM_NAME, "299.00", "通用", null, 1);

        ServiceItemPageQuery byCategory = new ServiceItemPageQuery();
        byCategory.setCategoryId(catAId);
        IPage<ServiceItemVO> categoryPage = serviceItemService.pageForAdmin(byCategory);
        assertThat(categoryPage.getTotal()).isEqualTo(2);

        ServiceItemPageQuery byStatus = new ServiceItemPageQuery();
        byStatus.setCategoryId(catAId);
        byStatus.setStatus(1);
        assertThat(serviceItemService.pageForAdmin(byStatus).getTotal()).isEqualTo(1);

        ServiceItemPageQuery byName = new ServiceItemPageQuery();
        byName.setCategoryId(catAId);
        byName.setName(ITEM_NAME_2);
        IPage<ServiceItemVO> namePage = serviceItemService.pageForAdmin(byName);
        assertThat(namePage.getTotal()).isEqualTo(1);
        assertThat(namePage.getRecords().get(0).getName()).isEqualTo(ITEM_NAME_2);
    }

    @Test
    void pageForAdminShouldReturnPageSizeRecords() {
        addItem(catAId, ITEM_NAME + 1, "99.00", "猫", null, 1);
        addItem(catAId, ITEM_NAME + 2, "99.00", "猫", null, 1);
        addItem(catAId, ITEM_NAME + 3, "99.00", "猫", null, 1);

        ServiceItemPageQuery query = new ServiceItemPageQuery();
        query.setCategoryId(catAId);
        query.setPage(1);
        query.setPageSize(2);

        IPage<ServiceItemVO> page = serviceItemService.pageForAdmin(query);
        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getPages()).isEqualTo(2);
    }

    @Test
    void pageForUserShouldOnlyReturnOnSaleAndSupportPriceRangeAndPetType() {
        addItem(catAId, ITEM_NAME, "100.00", "猫", null, 1);
        addItem(catAId, ITEM_NAME_2, "200.00", "狗", null, 1);
        addItem(catAId, ITEM_NAME + "下架", "150.00", "猫", null, 0);

        ServiceItemUserPageQuery all = new ServiceItemUserPageQuery();
        all.setCategoryId(catAId);
        assertThat(serviceItemService.pageForUser(all).getTotal()).isEqualTo(2);

        ServiceItemUserPageQuery byPetType = new ServiceItemUserPageQuery();
        byPetType.setCategoryId(catAId);
        byPetType.setPetType("猫");
        IPage<ServiceItemSimpleVO> petTypePage = serviceItemService.pageForUser(byPetType);
        assertThat(petTypePage.getTotal()).isEqualTo(1);
        assertThat(petTypePage.getRecords().get(0).getName()).isEqualTo(ITEM_NAME);

        ServiceItemUserPageQuery byPrice = new ServiceItemUserPageQuery();
        byPrice.setCategoryId(catAId);
        byPrice.setMinPrice(new BigDecimal("150.00"));
        byPrice.setMaxPrice(new BigDecimal("250.00"));
        IPage<ServiceItemSimpleVO> pricePage = serviceItemService.pageForUser(byPrice);
        assertThat(pricePage.getTotal()).isEqualTo(1);
        assertThat(pricePage.getRecords().get(0).getName()).isEqualTo(ITEM_NAME_2);
    }

    @Test
    void pageForUserShouldRejectInvalidPriceRange() {
        ServiceItemUserPageQuery query = new ServiceItemUserPageQuery();
        query.setMinPrice(new BigDecimal("300.00"));
        query.setMaxPrice(new BigDecimal("100.00"));

        assertThatThrownBy(() -> serviceItemService.pageForUser(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最低价格不能大于最高价格");
    }

    // ==================== 用户端详情 ====================

    @Test
    void detailForUserShouldReturnDetail() {
        Long id = addItem(catAId, ITEM_NAME, "99.00", "猫", "描述内容", 1);

        ServiceItemDetailVO detail = serviceItemService.detailForUser(id);

        assertThat(detail.getId()).isEqualTo(id);
        assertThat(detail.getName()).isEqualTo(ITEM_NAME);
        assertThat(detail.getDescription()).isEqualTo("描述内容");
    }

    @Test
    void detailForUserShouldRejectOfflineOrNonexistent() {
        Long offlineId = addItem(catAId, ITEM_NAME, "99.00", "猫", null, 0);

        assertThatThrownBy(() -> serviceItemService.detailForUser(offlineId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务不存在或已下架");
        assertThatThrownBy(() -> serviceItemService.detailForUser(99999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务不存在或已下架");
    }

    // ==================== 辅助方法 ====================

    private Long addCategory(String name) {
        ServiceCategoryAddRequest request = new ServiceCategoryAddRequest();
        request.setName(name);
        request.setSort(0);
        request.setStatus(1);
        return serviceCategoryService.add(request);
    }

    private Long addItem(Long categoryId, String name, String price, String petType, String description, int status) {
        ServiceItemAddRequest request = new ServiceItemAddRequest();
        request.setCategoryId(categoryId);
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        request.setPetType(petType);
        request.setDescription(description);
        request.setStatus(status);
        return serviceItemService.add(request);
    }

    private void insertOrderDetail(Long serviceId, int orderStatus) {
        String orderNumber = "IT-ORD-" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO service_order (order_number, user_id, total_amount, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                orderNumber, 1L, new BigDecimal("99.00"), orderStatus);
        Long orderId = jdbcTemplate.queryForObject("SELECT id FROM service_order WHERE order_number = ?",
                Long.class, orderNumber);
        jdbcTemplate.update("INSERT INTO order_detail (order_id, service_id, name, price, quantity, amount, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                orderId, serviceId, "IT-订单快照", new BigDecimal("99.00"), 1, new BigDecimal("99.00"));
    }

}
