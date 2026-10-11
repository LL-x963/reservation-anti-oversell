import { createApp } from 'vue'

import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
// 全局样式（移动端适配）：放在 Element Plus 样式之后，便于覆盖组件默认样式
import './styles/index.css'

const app = createApp(App)

// 全局注册 Element Plus 全部图标，模板里可直接写 <el-icon><Search /></el-icon>
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
