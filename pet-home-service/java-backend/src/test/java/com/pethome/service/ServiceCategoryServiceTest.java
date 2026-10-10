package com.pethome.service;

import com.pethome.common.exception.BusinessException;
import com.pethome.dto.ServiceCategoryAddRequest;
import com.pethome.dto.ServiceCategoryUpdateRequest;
import com.pethome.entity.ServiceCategory;
import com.pethome.vo.ServiceCategorySimpleVO;
import com.pethome.vo.ServiceCategoryVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 服务分类服务层测试（覆盖业务规则，事务回滚不污染数据库）
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class ServiceCategoryServiceTest {

    private static final String NAME_A = "IT-基础洗护";
    private static final String NAME_B = "IT-高级美容";
    private static final String NAME_DISABLED = "IT-已禁用分类";

    @Autowired
    private ServiceCategoryService serviceCategoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void addShouldFillIdAndAuditFieldsWithDefaults() {
        ServiceCategoryAddRequest request = new ServiceCategoryAddRequest();
        request.setName(NAME_A);

        Long id = serviceCategoryService.add(request);

        ServiceCategory entity = serviceCategoryService.getById(id);
        assertThat(entity).isNotNull();
        assertThat(entity.getSort()).isZero();
        assertThat(entity.getStatus()).isEqualTo(1);
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void addShouldTrimName() {
        ServiceCategoryAddRequest request = new ServiceCategoryAddRequest();
        request.setName("   " + NAME_A + "   ");

        Long id = serviceCategoryService.add(request);

        assertThat(serviceCategoryService.getById(id).getName()).isEqualTo(NAME_A);
    }

    @Test
    void listAllForAdminShouldReturnAllOrderedBySort() {
        Long idA = addCategory(NAME_A, 2, 1);
        Long idB = addCategory(NAME_B, 1, 1);

        List<ServiceCategoryVO> list = serviceCategoryService.listAllForAdmin();

        // sort 升序：B(1) 应排在 A(2) 之前
        assertThat(list).extracting(ServiceCategoryVO::getId).containsSubsequence(idB, idA);
    }

    @Test
    void listEnabledForUserShouldOnlyReturnEnabledCategories() {
        addCategory(NAME_A, 1, 1);
        addCategory(NAME_B, 2, 0);

        List<ServiceCategorySimpleVO> list = serviceCategoryService.listEnabledForUser();

        assertThat(list).extracting(ServiceCategorySimpleVO::getName)
                .contains(NAME_A)
                .doesNotContain(NAME_B);
        assertThat(list).allSatisfy(vo -> {
            assertThat(vo.getId()).isNotNull();
            assertThat(vo.getName()).isNotBlank();
            assertThat(vo.getSort()).isNotNull();
        });
    }

    @Test
    void addShouldRejectDuplicatedName() {
        addCategory(NAME_A, 0, 1);

        assertThatThrownBy(() -> addCategory(NAME_A, 0, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类名称已存在");
    }

    @Test
    void addShouldRejectNameDuplicatedWithDisabledCategory() {
        addCategory(NAME_DISABLED, 0, 0);

        assertThatThrownBy(() -> addCategory(NAME_DISABLED, 0, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类名称已存在");
    }

    @Test
    void updateShouldSucceedAndAllowKeepingOwnName() {
        Long id = addCategory(NAME_A, 0, 1);

        serviceCategoryService.update(updateRequest(id, NAME_A, 9, 0));

        ServiceCategory entity = serviceCategoryService.getById(id);
        assertThat(entity.getSort()).isEqualTo(9);
        assertThat(entity.getStatus()).isZero();
    }

    @Test
    void updateShouldRejectDuplicatedName() {
        Long idA = addCategory(NAME_A, 0, 1);
        addCategory(NAME_B, 0, 1);

        assertThatThrownBy(() -> serviceCategoryService.update(updateRequest(idA, NAME_B, 0, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类名称已存在");
    }

    @Test
    void updateNonexistentCategoryShouldFail() {
        assertThatThrownBy(() -> serviceCategoryService.update(updateRequest(99999999L, NAME_A, 0, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类不存在");
    }

    @Test
    void deleteShouldSucceedWhenNoServiceItemRelated() {
        Long id = addCategory(NAME_A, 0, 1);

        serviceCategoryService.delete(id);

        assertThat(serviceCategoryService.getById(id)).isNull();
    }

    @Test
    void deleteShouldRejectWhenOnlineServiceItemRelated() {
        Long id = addCategory(NAME_A, 0, 1);
        insertServiceItem(id, 1);

        assertThatThrownBy(() -> serviceCategoryService.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务项目");
        assertThat(serviceCategoryService.getById(id)).isNotNull();
    }

    @Test
    void deleteShouldRejectWhenOnlyOfflineServiceItemRelated() {
        Long id = addCategory(NAME_A, 0, 1);
        insertServiceItem(id, 0);

        assertThatThrownBy(() -> serviceCategoryService.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务项目");
    }

    @Test
    void deleteNonexistentCategoryShouldFail() {
        assertThatThrownBy(() -> serviceCategoryService.delete(99999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类不存在");
    }

    private Long addCategory(String name, Integer sort, Integer status) {
        ServiceCategoryAddRequest request = new ServiceCategoryAddRequest();
        request.setName(name);
        request.setSort(sort);
        request.setStatus(status);
        return serviceCategoryService.add(request);
    }

    private ServiceCategoryUpdateRequest updateRequest(Long id, String name, Integer sort, Integer status) {
        ServiceCategoryUpdateRequest request = new ServiceCategoryUpdateRequest();
        request.setId(id);
        request.setName(name);
        request.setSort(sort);
        request.setStatus(status);
        return request;
    }

    private void insertServiceItem(Long categoryId, int status) {
        jdbcTemplate.update("INSERT INTO service_item (category_id, name, price, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                categoryId, "IT-测试服务项目", new BigDecimal("99.00"), status);
    }

}
