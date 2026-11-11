-- ============================================================
-- 开发 / 测试环境种子数据（可重复执行）
--
-- 该文件位于 classpath:db/dev，只在 dev profile 的 flyway.locations 中加载；
-- 生产环境不会执行，避免把演示账号带进生产库。
--
-- 所有语句必须幂等：ON DUPLICATE KEY UPDATE / NOT EXISTS。
-- ============================================================

-- 管理端管理员：admin / 123456（BCrypt）
INSERT INTO `employee` (`username`, `password`, `name`, `phone`, `sex`, `status`, `role`)
VALUES ('admin', '$2a$10$l1VUQ2bO.T167NU6Vb7zhOnYtaWA8qMxiPnACixVJvQYTOi.bqohe', '超级管理员',
        '13800000000', 1, 1, 'ADMIN')
ON DUPLICATE KEY UPDATE `name` = '超级管理员', `role` = 'ADMIN';

-- H5 演示用户：13800138000 / 123456（BCrypt），openid 为空表示未绑定微信
INSERT INTO `user` (`openid`, `name`, `password`, `phone`, `sex`)
VALUES (NULL, '演示用户', '$2a$10$zq2JYyKkyRhC9B.uV3PMl.kQagEmoxUETvtbiL2i2DWLD8EeapIOi', '13800138000', 0)
ON DUPLICATE KEY UPDATE `name` = '演示用户';

-- 演示用户的默认地址（按详细地址判重，避免重复插入）
INSERT INTO `address_book` (`user_id`, `consignee`, `sex`, `phone`, `province_name`, `city_name`,
                            `district_name`, `detail`, `label`, `is_default`)
SELECT u.id, '演示用户', 0, '13800138000', '浙江省', '杭州市', '西湖区',
       '文一西路 100 号 1 单元 101 室', '家', 1
FROM `user` u
WHERE u.phone = '13800138000'
  AND NOT EXISTS (SELECT 1
                  FROM `address_book` a
                  WHERE a.user_id = u.id
                    AND a.detail = '文一西路 100 号 1 单元 101 室');

-- 演示用户的宠物（uk_user_name 保证幂等）
INSERT INTO `pet_profile` (`user_id`, `name`, `breed`, `gender`, `birthday`, `weight`, `vaccine`, `remark`)
SELECT u.id, '布丁', '英国短毛猫', 2, '2023-05-01', 5.20, '已完成三联疫苗', '性格温顺，怕吹风机'
FROM `user` u
WHERE u.phone = '13800138000'
ON DUPLICATE KEY UPDATE `breed` = '英国短毛猫', `remark` = '性格温顺，怕吹风机';
