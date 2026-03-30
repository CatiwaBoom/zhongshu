import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import router from './router'
import App from './App.vue'

const app = createApp(App)
// 挂载插件
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')