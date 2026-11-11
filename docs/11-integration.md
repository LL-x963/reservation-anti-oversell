# 11 前后端联调与契约

## 1. 技术原理

### 1.1 开发期为什么要代理

前端 `localhost:5174`、后端 `localhost:8080` 是两个源，浏览器同源策略会拦请求。两种解法：

| 方案 | 做法 | 适用 |
|---|---|---|
| **开发代理**（本项目） | Vite `server.proxy` 把 `/api` 转发到 8080，浏览器只看到 5174 一个源 | 开发期最省事，**没有 CORS 问题** |
| 后端 CORS | 后端配 `Access-Control-Allow-*` | 前后端不同域且无法代理时（本项目 `WebMvcConfig.addCorsMappings` 也配了，作为直连调试的兜底） |

生产环境通常是 Nginx：静态托管 `dist/` + 反向代理 `/api` 到后端（本项目尚未做，见 [14 章](14-todo-and-optimization.md)）。

### 1.2 契约（Contract）优先

前后端分离最容易出问题的不是业务逻辑，而是**契约不一致**：字段名、类型、分页参数名、错误语义。控制手段：

1. **唯一事实来源**：后端 DTO/VO 是权威，前端 api 文件里写明参数与字段；
2. **用测试固化契约**：接口测试断言响应里真实存在的 JSON 字段（见 [12 章](12-testing.md)）；
3. **联调前先用探针打一次真实接口**：不猜、不假设。

## 2. 本项目怎么用

### 2.1 代理配置（两个前端一致）

```js
// pet-home-admin/vite.config.js（端口 5173）与 pet-home-h5/vite.config.js（端口 5174）
server: {
  host: '0.0.0.0',
  port: 5174,
  open: true,
  proxy: {
    '/api':    { target: 'http://localhost:8080', changeOrigin: true },
    '/upload': { target: 'http://localhost:8080', changeOrigin: true }   // 上传图片的静态资源路径
  }
}
```

`/upload` 也要代理的原因：后端 `WebMvcConfig` 把本地目录 `./upload` 映射成 `/upload/**`，前端页面上的图片地址是 `/upload/2026/10/11/xxx.jpg`——**相对路径**，不代理就会 404。

### 2.2 契约核对的标准三步法（本项目的实践经验）

1. **读后端**：找到对应的 DTO / VO / 枚举（`dto/`、`vo/`、`common/enums/`），确认字段名、类型、必填与长度约束；
2. **读接口测试**：`src/test/java/com/pethome/web/XxxApiTest.java` 里断言的 JSON 字段名就是线上真实返回的字段（比读代码更可靠，因为测试是跑过的）；
3. **起服务打一次探针**：用真实 token 调一次，打印字段清单。例如验证订单列表：

   ```powershell
   # 登录拿 token → 带着 Bearer 调接口 → 打印字段清单
   $login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/user/login' `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([Text.Encoding]::UTF8.GetBytes('{"phone":"13800138000","password":"123456"}'))
   Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/user/order/page?page=1&pageSize=10' `
            -Headers @{ Authorization = 'Bearer ' + $login.data.token } | % data | % records | Select -First 1
   ```

   探针这一步在本项目多次抓到「文档/直觉与真实返回不一致」的问题（见下方踩坑）。

### 2.3 静态资源与上传

- 上传接口：`POST /api/admin/file/upload`（form-data，字段名固定 `file`），返回可访问地址 `/upload/yyyy/MM/dd/xxx.png`；
- 后端限制：单文件 5MB，允许 `jpg/jpeg/png/gif/webp`（`application.yml` 的 `pethome.upload.*`）；
- 前端：管理端用 `el-upload` + 自定义上传（走统一 axios 带 token）。

### 2.4 启动顺序（联调前必看）

```
MySQL(3306) → Redis(6379) → RabbitMQ(5672，含延迟插件) → Ollama(11434)
   → rag-service(8000) 可选（不启动时 AI 问答会降级为 bizCode=600，不影响其它功能）
   → java-backend(8080) → pet-home-admin(5173) / pet-home-h5(5174)
```

### 2.5 跨模块的契约要点（速查）

| 模块 | 要点 |
|---|---|
| 鉴权 | 请求头 `Authorization: Bearer {token}`；未登录 **HTTP 401** + `{code:0,msg}` |
| 统一返回 | `code=1` 成功（数据在 `data`）、`code=0` 失败（`msg` 给人看、`bizCode` 给程序判断） |
| 分页 | 参数 `page` / `pageSize`（**不是 `pageNum`**，`pageSize ≤ 100`），响应 `{records,total,current,size}` |
| 金额 | 后端 `BigDecimal` → JSON 数字；前端 `Number(x).toFixed(2)` |
| 比率 | `ratio` / `repurchaseRate` 是 0~1 小数，展示前 ×100 |
| 空值 | 后端 Jackson 配 `non_null`，**null 字段不会出现在 JSON 里** |
| 用户端 VO | 只含必要字段（不含 `status`、`createTime`、`phone` 等管理字段） |

## 3. 易错点与实际踩过的坑

1. **`pageNum` 与 `page`（本项目反复核对过）**：需求描述里常写 `pageNum`，后端 DTO 实际是 `page`。传错的症状是「分页永远第一页、总数对得上但内容不变」。

2. **`BigDecimal` 是 JSON 数字，不是字符串**：我曾据控制台显示判断「价格是字符串」，实测类型是 `Decimal`（JSON 数字）。**结论：不要靠肉眼判断类型，用探针打印类型**；前端用 `Number()` 转换可同时兼容两种。

3. **`non_null` 让字段"消失"**：详情响应里没有 `comment` 键，不代表 VO 没有该字段（未评价时为 null 被省略）。前端要做真值判断。

4. **HTTP 401 与业务 `code=0` 要分开处理**：401 才能触发"清登录态跳登录"，业务失败（如「该时段不可约」）只是提示。

5. **改了 Java「已存在文件」后必须用 `mvn clean test` 验证**：Maven 增量编译有时检测不到修改，运行的是旧 class 文件。本项目的实际表现是「新加的 DELETE 接口一直报 405 请求方式不支持」，`clean` 后才暴露出真正的编译错误（缺 `DeleteMapping` 导入）。**联调中"改了没生效"优先怀疑这里**。

6. **8080 被占用时的表现**：前端代理会报 `ECONNREFUSED`/502，容易被误判为"接口写错了"。启动后端前先确认端口空闲（本项目测试脚本里都会先探活 8080）。

7. **跨端 token 不通用**：管理端 token 调用户端接口会因 `userType` 不匹配被 401（有测试守着），别拿错 token 去排错。
