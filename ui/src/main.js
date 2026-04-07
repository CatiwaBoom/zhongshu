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

// 监听用户活动，用于滑动会话（60 分钟无操作视为过期）
const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll', 'visibilitychange']
function setLastActivity() {
	try { localStorage.setItem('lastActivity', String(Date.now())) } catch (e) {}
}
ACTIVITY_EVENTS.forEach(evt => window.addEventListener(evt, setLastActivity, { passive: true }))
// 初始化最后活动时间
setLastActivity()
