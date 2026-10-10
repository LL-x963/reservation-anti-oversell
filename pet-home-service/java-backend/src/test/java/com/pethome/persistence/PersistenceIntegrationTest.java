package com.pethome.persistence;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pethome.entity.User;
import com.pethome.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis-Plus 数据层集成测试（需本地 MySQL 且已导入 pet_home 库）
 *
 * <p>覆盖：主键自增回填、公共字段自动填充、分页插件。</p>
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class PersistenceIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertShouldFillIdAndAuditFields() {
        User user = new User();
        user.setOpenid("it_fill_" + System.currentTimeMillis());
        user.setName("自动填充测试");

        userMapper.insert(user);

        // 主键自增回填
        assertThat(user.getId()).isNotNull().isPositive();
        // 公共字段自动填充
        assertThat(user.getCreateTime()).isNotNull();
        assertThat(user.getUpdateTime()).isNotNull();
    }

    @Test
    void updateShouldFillUpdateTime() {
        User user = new User();
        user.setOpenid("it_update_" + System.currentTimeMillis());
        userMapper.insert(user);

        // 模拟业务更新：只构造需要变更的字段，updateTime 留空由填充器赋值
        User updateEntity = new User();
        updateEntity.setId(user.getId());
        updateEntity.setName("更新后的名字");
        userMapper.updateById(updateEntity);

        assertThat(updateEntity.getUpdateTime()).isNotNull();

        User reloaded = userMapper.selectById(user.getId());
        assertThat(reloaded.getName()).isEqualTo("更新后的名字");
        assertThat(reloaded.getUpdateTime()).isNotNull();
        assertThat(reloaded.getCreateTime()).isNotNull();
    }

    @Test
    void selectPageShouldUsePaginationInterceptor() {
        Page<User> page = userMapper.selectPage(new Page<>(1, 2), null);

        assertThat(page).isNotNull();
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getRecords().size()).isLessThanOrEqualTo(2);
        assertThat(page.getTotal()).isGreaterThanOrEqualTo(0);
        assertThat(page.getPages()).isEqualTo(page.getTotal() == 0 ? 0 : (page.getTotal() + 1) / 2);
    }

    @Test
    void selectPageSecondPageShouldNotContainDuplicatedIds() {
        Page<User> firstPage = userMapper.selectPage(new Page<>(1, 2), null);
        Page<User> secondPage = userMapper.selectPage(new Page<>(2, 2), null);

        firstPage.getRecords().forEach(first ->
                assertThat(secondPage.getRecords())
                        .extracting(User::getId)
                        .doesNotContain(first.getId()));
    }

    @Test
    void auditFieldsShouldBeLocalDateTime() {
        User user = new User();
        user.setOpenid("it_type_" + System.currentTimeMillis());
        userMapper.insert(user);

        LocalDateTime createTime = user.getCreateTime();
        assertThat(createTime).isBeforeOrEqualTo(LocalDateTime.now());
    }

}
