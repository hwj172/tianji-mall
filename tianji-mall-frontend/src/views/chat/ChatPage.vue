<template>
  <div class="chat-page">
    <div class="chat-container">
      <!-- 消息列表 -->
      <div class="chat-messages" ref="msgContainer">
        <div v-if="messages.length === 0" class="chat-placeholder">
          <div class="cp-icon">🤖</div>
          <h3>AI 智能导购</h3>
          <p>告诉我你想找什么，我来帮你推荐</p>
          <div v-if="hints.length" class="cp-hints">
            <el-tag v-for="h in hints" :key="h" @click="sendQuick(h)" class="hint-tag">{{ h }}</el-tag>
          </div>
        </div>

        <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
          <div class="msg-avatar">
            <span v-if="msg.role === 'user'">👤</span>
            <span v-else>🤖</span>
          </div>
          <div class="msg-bubble" :class="{ 'has-products': msg.products && msg.products.length }">
            <div class="msg-text">{{ msg.content }}</div>
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
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import ProductCard from '@/components/common/ProductCard.vue'
import { sendChatMessage, getChatHistory, getHotKeywords } from '@/api'

const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const sessionId = ref(null)
const msgContainer = ref(null)
const hints = ref([])

onMounted(async () => {
  // 恢复上次会话的 sessionId，历史走后端加载
  // 兼容旧格式残留：历史版本曾在 localStorage 存完整对象 {sessionId, messages}，此处只提取 sessionId
  let saved = localStorage.getItem('chat_session')
  if (saved) {
    try {
      const parsed = JSON.parse(saved)
      if (parsed && typeof parsed.sessionId === 'string') saved = parsed.sessionId
    } catch { /* 纯字符串，直接使用 */ }
    sessionId.value = saved
    try {
      const res = await getChatHistory(saved)
      if (res.data && res.data.length) {
        messages.value = res.data.map(c => ({ role: c.role, content: c.content }))
      }
    } catch { /* 历史加载失败则展示欢迎语 */ }
  }
  loadHotKeywords()
  await nextTick()
  scrollBottom()
})

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
        products: res.data.products || []
      })
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

function scrollBottom() {
  if (msgContainer.value) {
    msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  }
}
</script>

<style scoped>
.chat-page { max-width: 800px; margin: 0 auto; height: calc(100vh - 120px); }
.chat-container { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 8px; overflow: hidden; }

/* 消息区 */
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

/* 推荐商品（复用 ProductCard 竖卡，简单网格） */
.msg-products { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; margin-top: 10px; }

/* 输入框 */
.chat-input { padding: 16px; border-top: 1px solid #f0f0f0; }
</style>
