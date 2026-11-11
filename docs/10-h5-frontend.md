# 10 用户端 H5（pet-home-h5）

> 本项目的 H5 是纯手写移动端工程（未用 UI 框架的移动端套件），因此移动端适配、滚动加载、弹层交互这些细节都是自己实现的——**这一章的内容复用性最高**。

## 1. 技术原理

### 1.1 移动端适配的三件事

1. **视口**：`<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no, viewport-fit=cover">`
   - `user-scalable=no` 禁缩放（避免双击放大破坏布局）；
   - `viewport-fit=cover` 允许内容延伸进刘海屏区域，**这是使用安全区域变量的前提**；
2. **安全区域**：刘海屏/底部手势条用 `env(safe-area-inset-*)` 预留：
   ```css
   :root { --safe-area-top: env(safe-area-inset-top, 0px);
           --safe-area-bottom: env(safe-area-inset-bottom, 0px); }
   ```
3. **键盘弹出**：移动端软键盘会遮挡 `position: fixed; bottom: 0` 的元素。现代解法是用 `dvh`（动态视口高度）替代 `vh`：`height: 78dvh` 会随可视视口变化，键盘弹出时弹层自动上移。

### 1.2 无限滚动：IntersectionObserver 优于 scroll 事件

监听 `scroll` 需要自己算 `scrollTop + clientHeight >= scrollHeight - threshold`，且滚动时高频触发（要节流）。**哨兵元素 + IntersectionObserver** 更省：

```js
observer = new IntersectionObserver((entries) => {
  if (entries[0]?.isIntersecting) loadMore()
}, { rootMargin: '100px' })       // 提前 100px 触发，滑到即加载
observer.observe(sentinelRef.value)
```

### 1.3 弹层选型

| 场景 | 选择 | 理由 |
|---|---|---|
| 复杂列表选择（地址、宠物） | **底部抽屉 `el-drawer direction="btt"`** | 能展示多行信息（联系人/电话/完整地址），符合移动端手势习惯 |
| 简短选项（美容师） | `el-select` | 选项少、单行即可 |
| 表单（新增/编辑） | `el-dialog` | 居中弹窗 + 底部按钮 |
| 聊天 | 自定义底部弹层 | 需要固定头部/内容滚动/固定输入框三段式 |

## 2. 本项目怎么用

### 2.1 工程与全局适配

- Vite：端口 **5174**，代理 `/api` 与 `/upload` 到 8080，`host: '0.0.0.0'` 便于**手机真机**访问（`http://<电脑IP>:5174`）；
- `src/styles/index.css`：把安全区域、TabBar 高度、页面内边距收敛成 CSS 变量，页面只引用变量：

  ```css
  :root {
    --safe-area-top: env(safe-area-inset-top, 0px);
    --safe-area-bottom: env(safe-area-inset-bottom, 0px);
    --h5-navbar-height: 44px;
    --h5-tabbar-height: 52px;
    --h5-page-padding: 12px;
  }
  ```

  同时处理「去点击高亮、禁止页面橡皮筋滚动、禁止字体自动放大、`.el-message` 避开刘海、`.el-dialog` 窄屏自适应」等细节。

- 布局（`src/layout/Layout.vue`）：顶栏（非 Tab 页显示返回箭头）+ 内容滚动区 + 固定 TabBar，内容区 `padding-bottom` 预留 TabBar + 安全区域高度；右下角挂全局 `ChatDialog`。

### 2.2 路由与守卫

hash 模式 + 5 个 Tab 路由（`/home`、`/service`、`/cart`、`/order`、`/mine`）+ 3 个详情型路由（`/service/:id`、`/submit-order`、`/pet`）；守卫未登录跳 `/login?redirect=...`，登录成功后回跳原页面。

### 2.3 请求封装的两个特化

```js
// 1. 401 并发去重：多个请求同时 401，只提示一次、只跳一次
let redirecting = false
function handleUnauthorized(message) { clearAuth(); if (redirecting) return; redirecting = true; ... }

// 2. silent：让调用方自行展示失败态（AI 聊天用），避免"消息流内降级提示 + 全局 toast"重复
if (!response.config.silent) ElMessage.error(res.msg || '操作失败')
```

### 2.4 AI 问答组件（`src/components/ChatDialog.vue`）

交互要点（都是移动端聊天场景的通用做法）：

- **会话只存内存**：需求是「关闭弹窗不丢失、刷新页面清空」。用 `sessionStorage` 反而会在刷新后恢复（它按标签页存活），所以用组件内存 + `v-show` 切换显隐（组件不销毁）——关闭不丢、刷新即清空；
- **参考来源折叠**：`el-collapse` 收纳文档标题与片段，避免长文本刷屏；
- **免责声明**：后端答案**已包含**同类话术，前端先做尾部剥离再统一渲染灰色小字，否则会出现两遍；
- **中文输入法回车**：`isComposing || keyCode === 229` 时不发送（否则选词回车会误发）；`Shift + Enter` 换行；
- **失败降级**：接口失败/超时 → 消息流内插入「知识库服务暂时繁忙，请稍后再试」，而不是弹全局错误；
- **自动滚底**：`watch` 消息数量与 loading，`nextTick` 后 `scrollTop = scrollHeight`。

### 2.5 列表页与购物车的移动端细节

| 细节 | 做法 |
|---|---|
| 下滑加载更多 | 哨兵元素**始终渲染**（不要在 `v-if="list.length"` 内），否则首屏没有元素可观察，永远不触发 |
| 数量加减 | **本地先改（小计/合计立即变化）→ 调接口 → 失败回滚**；同一行有请求在飞时禁用该行按钮，避免并发覆盖 |
| 加购按钮 | `@click.stop`，否则点加购会同时触发卡片跳详情 |
| 底部结算栏 | 页面上有 TabBar 时，固定栏要 `bottom: calc(var(--h5-tabbar-height) + var(--safe-area-bottom))`，否则被 TabBar 压住 |
| 订单状态标签 | `el-tag` 的 `type` 定色系、`effect` 再区分同色系状态（服务中浅绿 / 已完成深绿） |
| 无图占位 | 图片为空时渲染「暂无图片」文本块，而不是空的 `<img>` |

## 3. 易错点与实际踩过的坑

1. **`sessionStorage` 不等于「刷新清空」**：它只随标签页关闭而清空，F5 刷新会保留。要「刷新清空」只能用内存变量。
2. **`v-if` 会销毁组件、`v-show` 不会**：会话/滚动位置这类状态要在弹层关闭后保留，就必须避免用 `v-if` 挂载弹层（或把状态提到组件外，如模块作用域）。
3. **固定定位元素要同时考虑两层**：底部安全区域（`env(...)`）**和** TabBar 高度；只处理其中一个，都会出现「按钮被遮住」。
4. **软键盘遮挡**：`height: 100vh` 在键盘弹出时不会变，用 `dvh` 才会随可视视口收缩；弹层用 `78dvh` 后，输入框自然浮在键盘上方。
5. **IntersectionObserver 的 rootMargin**：默认在元素「进入视口」时才触发，用户会看到明显空白等待；给 `rootMargin: '100px'` 提前加载体验更好。
6. **金额字段是 JSON 数字**：后端 `BigDecimal` 序列化成数字（如 `99.00` → `99.00`），但**跨语言/版本可能有差异**，所以统一用 `Number(value)` 转换后再 `toFixed(2)`，别直接字符串拼接。
7. **移动端点击反馈**：全局 `-webkit-tap-highlight-color: transparent` + `touch-action: manipulation`（去掉 300ms 延迟），列表项加 `.h5-tap` 类统一处理。
8. **安全提醒（未实现项）**：token 存在 `localStorage`，存在 XSS 窃取风险；正式上线建议改用 httpOnly Cookie + CSRF 防护，或至少做内容转义与 CSP。见 [14 章](14-todo-and-optimization.md)。
