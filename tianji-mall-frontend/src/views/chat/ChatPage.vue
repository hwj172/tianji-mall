<template>
  <div class="chat-page">
    <div class="chat-wrap">
      <!-- 会话侧栏 -->
      <aside class="chat-sidebar">
        <div class="cs-header">
          <span class="cs-title-label">会话列表</span>
          <el-button size="small" text type="primary" @click="newSession">＋ 新会话</el-button>
        </div>
        <div class="cs-list">
          <div
            v-for="s in sessions" :key="s.sessionId"
            class="cs-item" :class="{ active: s.sessionId === sessionId }"
            @click="switchSession(s.sessionId)"
          >
            <div class="cs-title">{{ s.title }}</div>
            <div class="cs-meta">{{ s.messageCount }} 条 · {{ fmtTime(s.lastTime) }}</div>
          </div>
          <el-empty v-if="!sessions.length" description="暂无会话" :image-size="60" />
        </div>
      </aside>

      <!-- 主聊天区 -->
      <div class="chat-container">
        <!-- 消息列表 -->
        <div class="chat-messages" ref="msgContainer">
          <div v-if="messages.length === 0" class="chat-placeholder">
            <div class="cp-icon">🤖</div>
            <h3>AI 智能导购</h3>
            <p>你好，我是天机商城的 AI 导购，可以帮你找商品、查订单、加购物车</p>
            <div v-if="quickPrompts.length" class="cp-hints">
              <el-tag v-for="q in quickPrompts" :key="q" @click="sendQuick(q)" class="hint-tag">{{ q }}</el-tag>
            </div>
          </div>

          <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
            <div class="msg-avatar">
              <span v-if="msg.role === 'user'">👤</span>
              <span v-else>🤖</span>
            </div>
            <div class="msg-bubble" :class="{ 'has-products': msg.products && msg.products.length }">
              <div class="msg-text">{{ msg.content }}</div>
              <!-- 工具执行过程 -->
              <div class="msg-tools" v-if="msg.toolExecutions && msg.toolExecutions.length">
                <span v-for="(t, i) in msg.toolExecutions" :key="i" class="tool-chip" :class="t.status">
                  {{ t.status === 'success' ? '✔' : '✘' }} {{ t.action }}
                </span>
              </div>
              <!-- 推荐商品 -->
              <div class="msg-products" v-if="msg.products && msg.products.length">
                <ProductCard v-for="p in msg.products" :key="p.id" :product="p" />
              </div>
            </div>
          </div>

          <div v-if="sending" class="msg-row assistant">
            <div class="msg-avatar">🤖</div>
            <div class="msg-bubble typing">思考中...</div>
          </div>
        </div>

        <!-- 输入框 -->
        <div class="chat-input">
          <div class="ci-tools">
            <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="handleFile" />
            <el-button size="small" text type="primary" @click="fileInput.click()" :disabled="sending">📷 传图搜商品</el-button>
          </div>
          <el-input
            v-model="inputText"
            placeholder="输入你的需求，如：推荐一款蓝牙耳机..."
            @keyup.enter="sendMessage"
            :disabled="sending"
            size="large"
          >
            <template #append>
              <el-button type="primary" @click="sendMessage" :loading="sending" :icon="Promotion">发送</el-button>
            </template>
          </el-input>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import ProductCard from '@/components/common/ProductCard.vue'
import { sendChatMessage, getChatHistory, getChatSessions, getHotKeywords, imageSearch } from '@/api'
import { fmtTime } from '@/utils/date'

const messages = ref([])
const sessions = ref([])
const inputText = ref('')
const sending = ref(false)
const sessionId = ref(null)
const msgContainer = ref(null)
const fileInput = ref(null)
const hints = ref([])

// AI 导购快捷问题（静态，替代仅热词）
const quickPrompts = [
  '推荐一款性价比高的手机',
  '帮我找蓝牙耳机',
  '我的订单到哪了？',
  '看看我的购物车'
]

onMounted(async () => {
  loadSessions()
  // 恢复上次会话的 sessionId，历史走后端加载（兼容旧 localStorage 对象格式；localStorage 不可用时静默跳过）
  let saved = null
  try {
    saved = localStorage.getItem('chat_session')
  } catch { /* localStorage 不可用 */ }
  if (saved) {
    try {
      const parsed = JSON.parse(saved)
      // 兼容三种旧格式：纯字符串 / JSON 字符串（带引号）/ 旧对象 {sessionId, messages}
      if (typeof parsed === 'string') saved = parsed
      else if (parsed && typeof parsed.sessionId === 'string') saved = parsed.sessionId
    } catch { /* 非 JSON，纯字符串直接使用 */ }
    sessionId.value = saved
    await loadHistory(saved)
  }
  loadHotKeywords()
  await nextTick()
  scrollBottom()
})

async function loadSessions() {
  try {
    const res = await getChatSessions()
    sessions.value = res.data || []
  } catch { /* 会话列表可选 */ }
}

async function loadHistory(sid) {
  try {
    const res = await getChatHistory(sid)
    if (res.data && res.data.length) {
      messages.value = res.data.map(c => ({
        role: c.role,
        content: c.content,
        products: c.products || []
      }))
    }
  } catch { /* 历史加载失败则展示欢迎语 */ }
  await nextTick()
  scrollBottom()
}

function newSession() {
  sessionId.value = null
  messages.value = []
  localStorage.removeItem('chat_session')
}

async function switchSession(sid) {
  if (sid === sessionId.value) return
  sessionId.value = sid
  localStorage.setItem('chat_session', sid)
  messages.value = []
  await loadHistory(sid)
}

async function loadHotKeywords() {
  try {
    const res = await getHotKeywords()
    if (res.data && res.data.length) hints.value = res.data.slice(0, 4)
  } catch { /* 热词可选，失败不展示 */ }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sending.value) return
  inputText.value = ''
  messages.value.push({ role: 'user', content: text })
  sending.value = true

  try {
    const res = await sendChatMessage({ sessionId: sessionId.value, message: text })
    if (res.data) {
      sessionId.value = res.data.sessionId
      localStorage.setItem('chat_session', sessionId.value)
      messages.value.push({
        role: 'assistant',
        content: res.data.reply,
        products: res.data.products || [],
        toolExecutions: res.data.toolExecutions || []
      })
      loadSessions()
    }
  } catch {
    messages.value.push({ role: 'assistant', content: '抱歉，服务暂时不可用，请稍后再试。' })
  } finally {
    sending.value = false
    await nextTick()
    scrollBottom()
  }
}

function sendQuick(hint) {
  inputText.value = hint
  sendMessage()
}

// 传图搜商品：图片压缩转 base64 data URL → 后端 VL-Embedding 相似度搜索 → 展示相似商品
// 注：不依赖上传接口返回的相对路径（/uploads/xxx，SiliconFlow 无法访问），直接传 data URL
async function handleFile(e) {
  const file = e.target.files && e.target.files[0]
  if (!file || sending.value) return
  messages.value.push({ role: 'user', content: '🔍 图片搜索：' + (file.name || '图片') })
  sending.value = true
  try {
    const dataUrl = await fileToBase64(file)
    const res = await imageSearch({ imageUrl: dataUrl })
    messages.value.push({
      role: 'assistant',
      content: '根据图片找到以下相似商品：',
      products: res.data || []
    })
  } catch {
    messages.value.push({ role: 'assistant', content: '图片搜索失败，请重试。' })
  } finally {
    sending.value = false
    if (e.target) e.target.value = ''
    await nextTick()
    scrollBottom()
  }
}

// 图片 → 压缩后 base64 data URL（限宽 600px、JPEG 0.85，控制体积避免请求超限）
function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      try {
        const MAX = 600
        let w = img.naturalWidth || MAX
        let h = img.naturalHeight || MAX
        const ratio = Math.min(MAX / w, MAX / h)
        if (ratio < 1) { w = Math.round(w * ratio); h = Math.round(h * ratio) }
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        canvas.getContext('2d').drawImage(img, 0, 0, w, h)
        resolve(canvas.toDataURL('image/jpeg', 0.85))
      } catch (err) {
        reject(err)
      } finally {
        URL.revokeObjectURL(url)
      }
    }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('图片解析失败')) }
    img.src = url
  })
}

function scrollBottom() {
  if (msgContainer.value) {
    msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  }
}
</script>

<style scoped>
.chat-page { max-width: 1100px; margin: 0 auto; height: calc(100vh - 120px); }
.chat-wrap { display: flex; height: 100%; gap: 14px; }

/* 会话侧栏 */
.chat-sidebar { width: 220px; background: #fff; border-radius: 8px; padding: 12px; display: flex; flex-direction: column; flex-shrink: 0; }
.cs-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.cs-title-label { font-size: 14px; font-weight: 600; }
.cs-list { flex: 1; overflow-y: auto; }
.cs-item { padding: 10px 12px; border-radius: 6px; cursor: pointer; margin-bottom: 4px; transition: background .15s; }
.cs-item:hover { background: #f5f5f5; }
.cs-item.active { background: #fff5f0; }
.cs-title { font-size: 13px; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cs-meta { font-size: 11px; color: #999; margin-top: 4px; }

/* 主聊天区 */
.chat-container { flex: 1; display: flex; flex-direction: column; background: #fff; border-radius: 8px; overflow: hidden; }
.chat-messages { flex: 1; overflow-y: auto; padding: 20px; }

.chat-placeholder { text-align: center; padding: 60px 20px; }
.cp-icon { font-size: 56px; margin-bottom: 12px; }
.chat-placeholder h3 { font-size: 20px; margin-bottom: 8px; }
.chat-placeholder p { font-size: 14px; color: #999; }
.cp-hints { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin-top: 20px; }
.hint-tag { cursor: pointer; }

.msg-row { display: flex; gap: 10px; margin-bottom: 16px; }
.msg-row.user { flex-direction: row-reverse; }
.msg-avatar { width: 36px; height: 36px; border-radius: 50%; background: #f0f0f0; display: flex; align-items: center; justify-content: center; font-size: 18px; flex-shrink: 0; }
.msg-bubble { max-width: 70%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; }
.msg-row.user .msg-bubble { background: #ff5000; color: #fff; border-bottom-right-radius: 4px; }
.msg-row.assistant .msg-bubble { background: #f5f5f5; border-bottom-left-radius: 4px; }
.msg-row.assistant .msg-bubble.has-products { max-width: 92%; }
.typing { color: #999; font-style: italic; }

.msg-tools { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.tool-chip { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: #e8f5e9; color: #2e7d32; }
.tool-chip.error { background: #fdecea; color: #c62828; }

.msg-products { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; margin-top: 10px; }

.chat-input { padding: 16px; border-top: 1px solid #f0f0f0; }
.ci-tools { margin-bottom: 8px; }
</style>
