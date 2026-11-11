# 09 管理后台前端（pet-home-admin）

## 1. 技术原理

### 1.1 后台系统的通用骨架

几乎任何后台都是同一套骨架：**登录页 → 主布局（侧边菜单 + 顶栏 + 内容区）→ 一堆「筛选 + 表格 + 分页 + 弹窗表单」的列表页**。把骨架搭好，页面就是重复劳动。

关键工程问题：

- **路由与菜单的一致性**：菜单和路由各写一份必然不同步；
- **权限**：未登录不能进页面（路由守卫）、接口 401 要统一处理（请求拦截器）；
- **组件库策略**：全量引入省心但产物大，按需引入体积小但要配插件。

### 1.2 ECharts 按需引入

`import * as echarts from 'echarts'` 会把所有图表类型打进来（几百 KB）。按需写法：

```js
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer])
```

## 2. 本项目怎么用

### 2.1 工程与入口

```js
// pet-home-admin/src/main.js
for (const [name, component] of Object.entries(ElementPlusIconsVue)) app.component(name, component)  // 图标全局注册
app.use(createPinia())    // Pinia 必须在 router 之前：路由守卫里会用到 store
app.use(router)
app.use(ElementPlus, { locale: zhCn })
```

依赖：Vue 3.5 + Vite 6 + Element Plus 2.9 + Pinia + ECharts 6（见 `pet-home-admin/package.json`）。

### 2.2 路由：菜单与路由单一数据源

`src/router/index.js` 把菜单项作为**唯一来源**导出，侧边栏直接消费它，避免菜单与路由两份维护：

```js
export const menuRoutes = [
  { path: '/dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'),
    meta: { title: '首页', icon: 'HomeFilled' } },
  { path: '/category',  ... meta: { title: '服务分类', icon: 'Menu' } },
  { path: '/service',   ... meta: { title: '服务项目', icon: 'Goods' } },
  { path: '/beautician',... meta: { title: '美容师管理', icon: 'UserFilled' } },
  { path: '/schedule',  ... meta: { title: '排班管理', icon: 'Calendar' } },
  { path: '/order',     ... meta: { title: '订单管理', icon: 'List' } },
  { path: '/stat',      ... meta: { title: '数据统计', icon: 'DataLine' } },
  { path: '/knowledge', ... meta: { title: '知识库管理', icon: 'Reading' } }
]
```

- 采用 **hash 模式**（`createWebHashHistory`）：部署时不需要服务端 rewrite；
- 守卫：未登录跳 `/login` 并带 `redirect`，已登录访问 `/login` 直接回首页，兜底路由回 `/dashboard`。

```js
router.beforeEach((to) => {
  const loggedIn = Boolean(getToken())
  if (!loggedIn && !to.meta.public) return { path: '/login', query: { redirect: to.fullPath } }
  if (loggedIn && to.path === '/login') return { path: '/' }
  return true
})
```

### 2.3 列表页的固定套路

以 `views/Order.vue`、`views/Service.vue` 为代表，每页都是：

1. 顶部筛选栏（`el-select` / `el-date-picker` / 搜索重置）；
2. `el-table` + `el-pagination`（`total/sizes/prev/pager/next/jumper`，切换每页回到第 1 页）；
3. 新增/编辑用 `el-dialog` + `el-form`（`rules` 与后端 DTO 校验一致）；
4. 每行操作按业务状态动态显示（订单页是最典型的「按状态出按钮」，与后端 `OrderStatus` 一一对应）；
5. 危险操作（删除/禁用/重建索引）统一二次确认。

### 2.4 异步任务状态轮询（`views/Knowledge.vue`）

知识库重建索引是异步任务，前端做法值得记住：

```
点击重建 → 按钮 loading → 立即刷新列表（此时文档变「索引中」）
        → 每 1.5s 轮询 GET indexStatus，最长 60s，看 rebuilding 转 false
        → 按结果提示（全部正常 / 部分失败 / 超时停止自动刷新）
        → 离开页面清理定时器；进入页面时若上次重建仍在进行则自动接续轮询
```

### 2.5 首页看板（`views/Dashboard.vue`）

- 四张数据卡片用 `el-statistic`，数据来自 8 个不同接口；
- 用 **`Promise.allSettled` 并行**：**单个接口失败只影响对应卡片**，不让整页空白；
- 趋势图用 ECharts 折线 + 渐变面积，今天的数据点高亮。

### 2.6 工具与封装

| 文件 | 作用 |
|---|---|
| `src/utils/request.js` | axios 封装：`Authorization: Bearer`、`code=1` 取 data、`code=0` 弹提示、HTTP 401 清登录态跳登录 |
| `src/utils/auth.js` | token 存取与跳转登录 |
| `src/utils/format.js` | `formatDecimal` / `formatDateTime` / `formatPercent`（后端比率是 0~1，展示前 ×100） |
| `src/api/*.js` | 每个业务模块一个文件，函数名与后端接口一一对应，并在注释里写清参数名差异 |

## 3. 易错点与实际踩过的坑

1. **日期格式 token 大小写（本项目真踩过）**：Element Plus 的 `value-format` 走 **day.js**，年份是全大写 `YYYY-MM-DD`；而后端 Java 的 `@JsonFormat(pattern = "yyyy-MM-dd")` 是小写 `yyyy`。写成 `yyyy-MM-dd` 时 Element Plus 会按字面输出（星期/年份解析错乱），表现为「提交的日期后端解析失败」。**口诀：前端 day.js 全大写，后端 Java 全小写**。

2. **路由与菜单不要各写一份**：本项目把 `menuRoutes` 导出给侧边栏复用。若新增页面只写了路由没加菜单，页面能访问但菜单里找不到，排查起来很费时间。

3. **Pinia 必须在 router 之前安装**：否则路由守卫里 `useXxxStore()` 会报「no active Pinia」。

4. **ECharts 实例要管生命周期**：`onMounted` 后 `init`、窗口变化 `resize`、组件卸载 `dispose`；否则会出现「图不显示」「切页面后内存泄漏」。本项目 `Stat.vue` 切粒度时用 `notMerge: true` 重绘，避免残留旧 series。

5. **全量引入组件库的代价**：本项目 `main.js` 全量引入 Element Plus 与全部图标，构建主包约 1.18MB（gzip ≈ 380KB）+ CSS 354KB。内网后台可接受，若要优化见 [14 章](14-todo-and-optimization.md)。

6. **`el-image` 的容错**：图片地址可能为空，先 `v-if="row.image"` 再渲染，避免出现破图占位。

7. **比率字段是 0~1**：后端 `ratio`、`repurchaseRate` 都存小数（如 `0.3333`），展示必须乘 100 并保留两位（`formatPercent`），直接把小数当百分比显示会「少 100 倍」。
