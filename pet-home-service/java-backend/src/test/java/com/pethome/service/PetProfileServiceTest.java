package com.pethome.service;

import com.pethome.common.context.UserContext;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.PetProfileAddRequest;
import com.pethome.dto.PetProfileUpdateRequest;
import com.pethome.entity.PetProfile;
import com.pethome.entity.User;
import com.pethome.mapper.UserMapper;
import com.pethome.vo.PetProfileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 宠物档案服务层测试（覆盖业务规则，事务回滚不污染数据库）
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class PetProfileServiceTest {

    private static final String PET_NAME = "IT-旺财";
    private static final String PET_NAME_2 = "IT-咪咪";

    @Autowired
    private PetProfileService petProfileService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userAId;
    private Long userBId;

    @BeforeEach
    void setUp() {
        userAId = insertUser("IT-PET-USER-A");
        userBId = insertUser("IT-PET-USER-B");
        // 模拟登录拦截器写入 ThreadLocal
        UserContext.set(userAId, UserType.USER);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==================== 新增 ====================

    @Test
    void addShouldBindCurrentUserAndFillAuditFields() {
        Long id = addPet(PET_NAME, "金毛", 1, LocalDate.of(2021, 5, 1), "59.90", "已接种三针");

        PetProfile entity = petProfileService.getById(id);
        assertThat(entity).isNotNull();
        // user_id 自动绑定为当前登录用户
        assertThat(entity.getUserId()).isEqualTo(userAId);
        assertThat(entity.getName()).isEqualTo(PET_NAME);
        assertThat(entity.getBirthday()).isEqualTo(LocalDate.of(2021, 5, 1));
        assertThat(entity.getWeight()).isEqualByComparingTo("59.90");
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void addShouldTrimName() {
        Long id = addPet("   " + PET_NAME + "   ", null, null, null, null, null);

        assertThat(petProfileService.getById(id).getName()).isEqualTo(PET_NAME);
    }

    @Test
    void addShouldFailWhenNotLoggedIn() {
        UserContext.clear();

        assertThatThrownBy(() -> addPet(PET_NAME, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未获取到登录用户");
    }

    @Test
    void addShouldRejectDuplicatedNameInSameUser() {
        addPet(PET_NAME, null, null, null, null, null);

        assertThatThrownBy(() -> addPet(PET_NAME, "泰迪", 2, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宠物名称已存在");
    }

    @Test
    void addShouldAllowSameNameForDifferentUser() {
        addPet(PET_NAME, null, null, null, null, null);

        UserContext.set(userBId, UserType.USER);
        Long otherId = addPet(PET_NAME, null, null, null, null, null);

        assertThat(petProfileService.getById(otherId).getUserId()).isEqualTo(userBId);
    }

    // ==================== 查询 ====================

    @Test
    void listShouldOnlyReturnCurrentUserPets() {
        addPet(PET_NAME, null, null, null, null, null);
        addPet(PET_NAME_2, null, null, null, null, null);

        UserContext.set(userBId, UserType.USER);
        addPet(PET_NAME, null, null, null, null, null);

        // 当前为 userB，只能看到自己的 1 只
        assertThat(petProfileService.listForCurrentUser()).hasSize(1);

        UserContext.set(userAId, UserType.USER);
        List<PetProfileVO> pets = petProfileService.listForCurrentUser();
        assertThat(pets).hasSize(2);
        assertThat(pets).allSatisfy(vo -> assertThat(vo.getUserId()).isEqualTo(userAId));
        assertThat(pets).extracting(PetProfileVO::getName).containsExactly(PET_NAME, PET_NAME_2);
    }

    @Test
    void detailShouldReturnOwnPet() {
        Long id = addPet(PET_NAME, "金毛", 1, LocalDate.of(2021, 5, 1), "59.90", null);

        PetProfileVO vo = petProfileService.detailForCurrentUser(id);

        assertThat(vo.getId()).isEqualTo(id);
        assertThat(vo.getBreed()).isEqualTo("金毛");
        assertThat(vo.getGender()).isEqualTo(1);
        assertThat(vo.getBirthday()).isEqualTo(LocalDate.of(2021, 5, 1));
    }

    @Test
    void detailShouldRejectOtherUsersPet() {
        Long id = addPet(PET_NAME, null, null, null, null, null);

        UserContext.set(userBId, UserType.USER);

        assertThatThrownBy(() -> petProfileService.detailForCurrentUser(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宠物不存在");
    }

    @Test
    void detailShouldRejectNonexistentPet() {
        assertThatThrownBy(() -> petProfileService.detailForCurrentUser(99999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宠物不存在");
    }

    // ==================== 修改 ====================

    @Test
    void updateShouldSucceedAndKeepUserBinding() {
        Long id = addPet(PET_NAME, null, null, null, null, null);

        petProfileService.update(updateRequest(id, PET_NAME + "改", "柯基", 1, "12.50", "已接种"));

        PetProfile entity = petProfileService.getById(id);
        assertThat(entity.getName()).isEqualTo(PET_NAME + "改");
        assertThat(entity.getBreed()).isEqualTo("柯基");
        assertThat(entity.getWeight()).isEqualByComparingTo("12.50");
        assertThat(entity.getUserId()).isEqualTo(userAId);
    }

    @Test
    void updateShouldAllowKeepingOwnName() {
        Long id = addPet(PET_NAME, null, null, null, null, null);

        petProfileService.update(updateRequest(id, PET_NAME, "新品种", null, null, null));

        assertThat(petProfileService.getById(id).getBreed()).isEqualTo("新品种");
    }

    @Test
    void updateShouldRejectDuplicatedName() {
        Long id = addPet(PET_NAME, null, null, null, null, null);
        addPet(PET_NAME_2, null, null, null, null, null);

        assertThatThrownBy(() -> petProfileService.update(updateRequest(id, PET_NAME_2, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宠物名称已存在");
    }

    @Test
    void updateShouldRejectOtherUsersPet() {
        Long id = addPet(PET_NAME, null, null, null, null, null);

        UserContext.set(userBId, UserType.USER);

        assertThatThrownBy(() -> petProfileService.update(updateRequest(id, PET_NAME_2, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宠物不存在");
    }

    // ==================== 删除 ====================

    @Test
    void deleteShouldSucceedWhenNoOrderRelated() {
        Long id = addPet(PET_NAME, null, null, null, null, null);

        petProfileService.delete(id);

        assertThat(petProfileService.getById(id)).isNull();
    }

    @Test
    void deleteShouldRejectWhenUnfinishedOrderExists() {
        for (int orderStatus : new int[]{0, 1, 2, 3}) {
            Long id = addPet(PET_NAME + orderStatus, null, null, null, null, null);
            insertOrder(id, orderStatus);

            assertThatThrownBy(() -> petProfileService.delete(id))
                    .as("订单状态 %s 属于未完成", orderStatus)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未完成订单");
        }
    }

    @Test
    void deleteShouldAllowWhenOnlyFinishedOrderExists() {
        for (int orderStatus : new int[]{4, 5, 6}) {
            Long id = addPet(PET_NAME + orderStatus, null, null, null, null, null);
            insertOrder(id, orderStatus);

            petProfileService.delete(id);

            assertThat(petProfileService.getById(id)).isNull();
        }
    }

    @Test
    void deleteShouldRejectOtherUsersPet() {
        Long id = addPet(PET_NAME, null, null, null, null, null);

        UserContext.set(userBId, UserType.USER);

        assertThatThrownBy(() -> petProfileService.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宠物不存在");
        // 未被删除
        assertThat(petProfileService.getById(id)).isNotNull();
    }

    // ==================== 辅助方法 ====================

    private Long insertUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setSex(0);
        userMapper.insert(user);
        return user.getId();
    }

    private Long addPet(String name, String breed, Integer gender, LocalDate birthday,
                        String weight, String vaccine) {
        PetProfileAddRequest request = new PetProfileAddRequest();
        request.setName(name);
        request.setBreed(breed);
        request.setGender(gender);
        request.setBirthday(birthday);
        if (weight != null) {
            request.setWeight(new BigDecimal(weight));
        }
        request.setVaccine(vaccine);
        return petProfileService.add(request);
    }

    private PetProfileUpdateRequest updateRequest(Long id, String name, String breed, Integer gender,
                                                  String weight, String vaccine) {
        PetProfileUpdateRequest request = new PetProfileUpdateRequest();
        request.setId(id);
        request.setName(name);
        request.setBreed(breed);
        request.setGender(gender);
        if (weight != null) {
            request.setWeight(new BigDecimal(weight));
        }
        request.setVaccine(vaccine);
        return request;
    }

    private void insertOrder(Long petId, int orderStatus) {
        jdbcTemplate.update("INSERT INTO service_order (order_number, user_id, pet_id, total_amount, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                "IT-PET-ORD-" + System.nanoTime(), userAId, petId, new BigDecimal("99.00"), orderStatus);
    }

}
