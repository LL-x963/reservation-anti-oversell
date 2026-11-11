# 04 数据层 MyBatis-Plus

## 1. 技术原理

### 1.1 MyBatis-Plus 解决什么

MyBatis 需要手写 SQL 与映射；MyBatis-Plus 在其上提供：

- `BaseMapper<T>` / `IService<T>`：单表 CRUD 不用写 SQL；
- `LambdaQueryWrapper`：用方法引用代替字符串列名（`User::getPhone`），**编译期就能发现字段写错**；
- 分页插件：`page(new Page<>(page, pageSize), wrapper)` 自动生成 `LIMIT`；
- 复杂 SQL 仍可写注解（`@Select`/`@Update`）或 XML，不牺牲灵活性。

### 1.2 N+1 问题

列表场景最容易犯的错：查出 20 条订单后，循环里逐条查「宠物名」，产生 1 + 20 次查询。正确做法是**批量查询 + 内存映射**：

```java
// 一次查出所有需要的宠物，再按 id 映射
Map<Long, String> petNames = getPetNameMap(ids);
```

## 2. 本项目怎么用

### 2.1 基础用法

```java
// Mapper 只需继承 BaseMapper，单表 CRUD 全白送
public interface ServiceOrderMapper extends BaseMapper<ServiceOrder> { }

// Service 继承 ServiceImpl 获得 lambdaQuery() 等能力
public class OrderServiceImpl extends ServiceImpl<ServiceOrderMapper, ServiceOrder> { }
```

链式查询与分页（`OrderServiceImpl.pageMyOrders`）：

```java
Page<ServiceOrder> page = page(new Page<>(query.getPage(), query.getPageSize()),
        new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getUserId, userId)
                .eq(query.getStatus() != null, ServiceOrder::getStatus, query.getStatus())  // 条件为 false 时该子句不拼
                .orderByDesc(ServiceOrder::getId));
return toVOPage(page);
```

> `eq(boolean condition, ...)` 这种「条件拼装」写法在本项目大量使用：一条查询同时服务「全部/带筛选」两种场景。

### 2.2 复杂 SQL 用注解写（并发关键）

乐观锁 CAS 这类必须靠数据库原子性保证的操作，用注解 SQL 最直观：

```java
// mapper/BeauticianScheduleMapper.java
@Update("UPDATE beautician_schedule SET available = 0, order_id = #{orderId} "
        + "WHERE id = #{scheduleId} AND available = 1")
int occupySlot(@Param("scheduleId") Long scheduleId, @Param("orderId") Long orderId);
```

还有 `releaseSlot`（按排班 id + 订单 id 精确释放）与 `releaseByOrderId`（兜底释放），细节见 [07 章](07-order-domain.md)。

### 2.3 批量查询装配 VO（防 N+1）

订单列表要显示「用户名 / 宠物名 / 美容师名 / 服务明细」，全部批量查（`OrderServiceImpl.toVOPage`）：

```java
Map<Long, String> beauticianNames = getNameMap(records.stream().map(ServiceOrder::getBeauticianId)...);
Map<Long, String> petNames        = getPetNameMap(...);
Map<Long, String> userNames       = getUserNameMap(...);
Map<Long, List<OrderItemVO>> itemsMap = getOrderItemsMap(records.stream().map(ServiceOrder::getId)...);
return page.convert(order -> toVO(order, beauticianNames, petNames, userNames, itemsMap));
```

明细的批量分组查询：

```java
orderDetailMapper.selectList(new LambdaQueryWrapper<OrderDetail>().in(OrderDetail::getOrderId, orderIds))
        .stream()
        .collect(Collectors.groupingBy(OrderDetail::getOrderId,
                 Collectors.mapping(this::toItemVO, Collectors.toList())));
```

### 2.4 分页与 VO 转换

`page.convert(...)` 直接把实体分页转成 VO 分页（保留 total/current/size），比手工 new 一个 Page 更省心。

### 2.5 配置要点（application.yml）

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true      # user_id → userId
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 控制台打印 SQL
  global-config:
    db-config:
      id-type: auto
```

## 3. 易错点与实际踩过的坑

1. **一级缓存：旁路更新后读到的还是旧值**（本项目真实踩过）
   同一 SqlSession 内，先用 `JdbcTemplate` 更新了 `address_book`，随后 `selectById` 仍返回旧数据——因为 MyBatis 一级缓存没被清。**修复**：测试里改为走 `addressBookMapper.updateById(...)`（走 MyBatis 会自动清缓存）。教训：**写测试造数时，尽量用同一套 MyBatis 通道，不要混用 JdbcTemplate 与 Mapper 交叉读写同一行**。

2. **`.one()` 在多行时会抛异常**
   `lambdaQuery().eq(...).one()` 命中多条会抛 `TooManyResultsException`。本项目按手机号查用户时统一加 `.last("LIMIT 1")`：
   ```java
   private User getByPhone(String phone) {
       return lambdaQuery().eq(User::getPhone, phone).last("LIMIT 1").one();
   }
   ```

3. **`BeanUtils.copyProperties` 是隐式契约**
   `ServiceItemServiceImpl.toSimpleVO()` 等大量使用它：**给 VO 加一个同名字段，就会自动带上值**。好处是加字段零成本（本项目给用户端列表加 `description` 就是一行），坏处是「字段名写错就静默为空」——加字段后**务必写一条测试断言它真的有值**。

4. **分页参数名是 `page`，不是 `pageNum`**
   需求文档里常写 `pageNum`，本项目 DTO 用的是 `page`（见 `OrderPageQuery`、`ServiceItemUserPageQuery`），`pageSize` 上限 100（`@Max(100)`）。前端传错参数名的表现是「分页永远在第一页」。

5. **不要给用户端返回管理端 VO**
   用户端有专用的 `XxxSimpleVO / XxxDetailVO`（不含 `status`、`createTime`、`phone`、`idNumber` 等），并有测试断言「用户端响应不含 status」。新增字段时注意别把管理字段顺手带出去。

6. **`updateById` 只更新非 null 字段**
   MyBatis-Plus 默认「忽略 null」，所以「把某字段置空」不能靠 `updateById(entityWithNullField)`，需要 `UpdateWrapper.set(field, null)`。本项目删除排班占用用的是注解 SQL，规避了这个坑。
