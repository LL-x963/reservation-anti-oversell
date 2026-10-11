package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.BeauticianAddRequest;
import com.pethome.dto.BeauticianPageQuery;
import com.pethome.dto.BeauticianUpdateRequest;
import com.pethome.entity.Beautician;
import com.pethome.vo.BeauticianVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 美容师服务层测试（覆盖业务规则，事务回滚不污染数据库）
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class BeauticianServiceTest {

    private static final String NAME_A = "IT-美容师小王";
    private static final String NAME_B = "IT-美容师小李";

    @Autowired
    private BeauticianService beauticianService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 新增 ====================

    @Test
    void addShouldDefaultScoreAndStatus() {
        Long id = addBeautician(NAME_A, Arrays.asList("洗护", "美容"), null, LocalDate.of(2024, 3, 1));

        Beautician entity = beauticianService.getById(id);
        assertThat(entity.getScore()).isEqualByComparingTo("5.00");
        assertThat(entity.getStatus()).isEqualTo(1);
        assertThat(entity.getEntryTime()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void addShouldStoreSkillTagsAsCommaSeparated() {
        Long id = addBeautician(NAME_A, Arrays.asList("洗护", "美容", "SPA"), null, null);

        // 数据库中为逗号分隔字符串
        String stored = jdbcTemplate.queryForObject("SELECT skill_tags FROM beautician WHERE id = ?",
                String.class, id);
        assertThat(stored).isEqualTo("洗护,美容,SPA");

        // 返回时还原为数组
        BeauticianPageQuery query = new BeauticianPageQuery();
        query.setName(NAME_A);
        IPage<BeauticianVO> page = beauticianService.pageForAdmin(query);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getSkillTags()).containsExactly("洗护", "美容", "SPA");
    }

    @Test
    void addShouldNormalizeSkillTags() {
        Long id = addBeautician(NAME_A,
                Arrays.asList("  洗护  ", "美容", "洗护", "", "   ", null), null, null);

        String stored = jdbcTemplate.queryForObject("SELECT skill_tags FROM beautician WHERE id = ?",
                String.class, id);
        // 去空格、去空项、去重且保持顺序
        assertThat(stored).isEqualTo("洗护,美容");
    }

    @Test
    void addShouldStoreEmptyStringWhenNoValidSkillTag() {
        Long id = addBeautician(NAME_A, Collections.emptyList(), null, null);

        String stored = jdbcTemplate.queryForObject("SELECT skill_tags FROM beautician WHERE id = ?",
                String.class, id);
        assertThat(stored).isEmpty();

        // 返回给前端仍是空数组
        BeauticianPageQuery query = new BeauticianPageQuery();
        query.setName(NAME_A);
        assertThat(beauticianService.pageForAdmin(query).getRecords().get(0).getSkillTags()).isEmpty();
    }

    @Test
    void addShouldRejectSkillTagsExceedingColumnLength() {
        String longTag = "技".repeat(50);
        List<String> tags = Arrays.asList(longTag, longTag + "a", longTag + "b", longTag + "c", longTag + "d", longTag + "e");

        assertThatThrownBy(() -> addBeautician(NAME_A, tags, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("技能标签总长度不能超过");
    }

    @Test
    void addShouldTrimName() {
        Long id = addBeautician("   " + NAME_A + "   ", null, null, null);

        assertThat(beauticianService.getById(id).getName()).isEqualTo(NAME_A);
    }

    @Test
    void addShouldKeepOffDutyStatusWhenSpecified() {
        Long id = addBeautician(NAME_A, null, 0, null);

        assertThat(beauticianService.getById(id).getStatus()).isZero();
    }

    // ==================== 分页与列表 ====================

    @Test
    void pageForAdminShouldFilterByNameTagAndStatus() {
        addBeautician(NAME_A, Arrays.asList("洗护", "美容"), 1, null);
        addBeautician(NAME_B, Arrays.asList("SPA"), 0, null);

        BeauticianPageQuery byName = new BeauticianPageQuery();
        // 用只属于测试数据的关键字做模糊匹配：开发库可能已有真实美容师“小王”
        byName.setName("美容师小王");
        IPage<BeauticianVO> namePage = beauticianService.pageForAdmin(byName);
        assertThat(namePage.getTotal()).isEqualTo(1);
        assertThat(namePage.getRecords().get(0).getName()).isEqualTo(NAME_A);

        BeauticianPageQuery byStatus = new BeauticianPageQuery();
        byStatus.setStatus(0);
        assertThat(beauticianService.pageForAdmin(byStatus).getTotal()).isEqualTo(1);

        BeauticianPageQuery byTag = new BeauticianPageQuery();
        byTag.setSkillTag("洗护");
        byTag.setPageSize(100);
        // 开发库可能已有真实美容师也带“洗护”标签，这里断言本次新增的记录被精确命中
        assertThat(beauticianService.pageForAdmin(byTag).getRecords())
                .extracting(BeauticianVO::getName).contains(NAME_A);

        // FIND_IN_SET 精确匹配，部分匹配不应命中
        BeauticianPageQuery byPartialTag = new BeauticianPageQuery();
        byPartialTag.setSkillTag("洗");
        assertThat(beauticianService.pageForAdmin(byPartialTag).getTotal()).isZero();
    }

    @Test
    void pageForAdminShouldReturnPageSizeRecords() {
        addBeautician(NAME_A + 1, null, null, null);
        addBeautician(NAME_A + 2, null, null, null);
        addBeautician(NAME_A + 3, null, null, null);

        BeauticianPageQuery query = new BeauticianPageQuery();
        query.setName("IT-美容师小王");
        query.setPage(1);
        query.setPageSize(2);

        IPage<BeauticianVO> page = beauticianService.pageForAdmin(query);
        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getPages()).isEqualTo(2);
    }

    @Test
    void listOnDutyShouldOnlyReturnOnDutyBeauticians() {
        addBeautician(NAME_A, null, 1, null);
        addBeautician(NAME_B, null, 0, null);

        List<BeauticianVO> list = beauticianService.listOnDuty();

        assertThat(list).extracting(BeauticianVO::getName).contains(NAME_A).doesNotContain(NAME_B);
        assertThat(list).allSatisfy(vo -> assertThat(vo.getStatus()).isEqualTo(1));
    }

    // ==================== 修改 ====================

    @Test
    void updateShouldUpdateFieldsIncludingSkillTagsAndStatus() {
        Long id = addBeautician(NAME_A, Arrays.asList("洗护"), 1, null);

        BeauticianUpdateRequest request = new BeauticianUpdateRequest();
        request.setId(id);
        request.setName(NAME_A + "改");
        request.setSkillTags(Arrays.asList("美容", "SPA"));
        request.setServiceRange("朝阳区");
        request.setEntryTime(LocalDate.of(2023, 1, 1));
        request.setStatus(0);
        beauticianService.update(request);

        Beautician entity = beauticianService.getById(id);
        assertThat(entity.getName()).isEqualTo(NAME_A + "改");
        assertThat(entity.getSkillTags()).isEqualTo("美容,SPA");
        assertThat(entity.getServiceRange()).isEqualTo("朝阳区");
        assertThat(entity.getEntryTime()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(entity.getStatus()).isZero();
    }

    @Test
    void updateShouldNotChangeScore() {
        Long id = addBeautician(NAME_A, null, 1, null);
        // 模拟评价体系回写评分
        jdbcTemplate.update("UPDATE beautician SET score = 4.50 WHERE id = ?", id);

        BeauticianUpdateRequest request = new BeauticianUpdateRequest();
        request.setId(id);
        request.setName(NAME_A + "改");
        request.setStatus(1);
        beauticianService.update(request);

        assertThat(beauticianService.getById(id).getScore()).isEqualByComparingTo("4.50");
    }

    @Test
    void updateShouldClearSkillTagsWhenEmpty() {
        Long id = addBeautician(NAME_A, Arrays.asList("洗护"), 1, null);

        BeauticianUpdateRequest request = new BeauticianUpdateRequest();
        request.setId(id);
        request.setName(NAME_A);
        request.setSkillTags(Collections.emptyList());
        request.setStatus(1);
        beauticianService.update(request);

        String stored = jdbcTemplate.queryForObject("SELECT skill_tags FROM beautician WHERE id = ?",
                String.class, id);
        assertThat(stored).isEmpty();
    }

    @Test
    void updateNonexistentBeauticianShouldFail() {
        BeauticianUpdateRequest request = new BeauticianUpdateRequest();
        request.setId(99999999L);
        request.setName(NAME_A);
        request.setStatus(1);

        assertThatThrownBy(() -> beauticianService.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("美容师不存在");
    }

    // ==================== 删除 ====================

    @Test
    void deleteShouldSucceedWhenNoUnfinishedOrder() {
        Long id = addBeautician(NAME_A, null, 1, null);

        beauticianService.delete(id);

        assertThat(beauticianService.getById(id)).isNull();
    }

    @Test
    void deleteShouldRejectWhenUnfinishedOrderExists() {
        for (int orderStatus : new int[]{1, 2, 3, 4}) {
            Long id = addBeautician(NAME_A + orderStatus, null, 1, null);
            insertOrder(id, orderStatus);

            assertThatThrownBy(() -> beauticianService.delete(id))
                    .as("订单状态 %s 属于未完成", orderStatus)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未完成订单");
        }
    }

    @Test
    void deleteShouldAllowWhenOnlyFinishedOrderExists() {
        for (int orderStatus : new int[]{5, 6, 7}) {
            Long id = addBeautician(NAME_A + orderStatus, null, 1, null);
            insertOrder(id, orderStatus);

            beauticianService.delete(id);

            assertThat(beauticianService.getById(id)).isNull();
        }
    }

    @Test
    void deleteNonexistentBeauticianShouldFail() {
        assertThatThrownBy(() -> beauticianService.delete(99999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("美容师不存在");
    }

    // ==================== 辅助方法 ====================

    private Long addBeautician(String name, List<String> skillTags, Integer status, LocalDate entryTime) {
        BeauticianAddRequest request = new BeauticianAddRequest();
        request.setName(name);
        request.setSkillTags(skillTags);
        request.setStatus(status);
        request.setEntryTime(entryTime);
        return beauticianService.add(request);
    }

    private void insertOrder(Long beauticianId, int orderStatus) {
        jdbcTemplate.update("INSERT INTO service_order (order_number, user_id, beautician_id, total_amount, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                "IT-BEA-ORD-" + System.nanoTime(), 1L, beauticianId, new BigDecimal("99.00"), orderStatus);
    }

}
