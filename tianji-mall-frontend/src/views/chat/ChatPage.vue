<template>
  <div class="chat-page">
    <div class="chat-container">
      <!-- 消息列表 -->
      <div class="chat-messages" ref="msgContainer">
        <div v-if="messages.length === 0" class="chat-placeholder">
          <div class="cp-icon">🤖</div>
          <h3>AI 智能导购</h3>
          <p>告诉我你想找什么，我来帮你推荐</p>
          <div class="cp-hints">
            <el-tag v-for="h in hints" :key="h" @click="sendQuick(h)" class="hint-tag">{{ h }}</el-tag>
          </div>
        </div>

        <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
          <div class="msg-avatar">
            <span v-if="msg.role === 'user'">👤</span>
            <span v-else>🤖</span>
          </div>
          <div class="msg-bubble">
            <div class="msg-text">{{ msg.content }}</div>
            <!-- 推荐商品 -->
            <div class="msg-products" v-if="msg.products && msg.products.length">
              <div class="rec-card" v-for="p in msg.products" :key="p.id" @click="$router.push(`/product/${p.id}`)">
                <div class="rec-img">
                  <el-image :src="getFirstImage(p.images)" fit="cover" @error="onImgError" />
                </div>
                <div class="rec-info">
                  <span class="rec-name">{{ p.name }}</span>
                  <span class="rec-desc">{{ p.description || '暂无简介' }}</span>
                  <span class="rec-price">¥{{ p.price }}</span>
                </div>
              </div>
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
import { ElMessage } from 'element-plus'
import { sendChatMessage } from '@/api'

const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const sessionId = ref(null)
const msgContainer = ref(null)

const hints = ['推荐一款蓝牙耳机', '有什么好用的洗面奶', '200元以内的运动鞋', '适合送礼的商品']

onMounted(() => {
  // 尝试恢复上次会话
  const saved = localStorage.getItem('chat_session')
  if (saved) {
    try {
      const data = JSON.parse(saved)
      sessionId.value = data.sessionId
      if (data.messages) messages.value = data.messages
    } catch { /* ignore */ }
  }
})

function saveSession() {
  localStorage.setItem('chat_session', JSON.stringify({
    sessionId: sessionId.value,
    messages: messages.value.slice(-20) // 只保留最近20条
  }))
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
    saveSession()
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

function getFirstImage(images) {
  if (!images) return ''
  try {
    const arr = typeof images === 'string' ? JSON.parse(images) : images
    return arr[0] || ''
  } catch { return '' }
}

function onImgError(e) {
  e.target.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 60"><rect fill="%23f5f5f5" width="60" height="60"/><text x="50%" y="50%" text-anchor="middle" dy=".3em" fill="%23ccc" font-size="8">无图</text></svg>'
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
.typing { color: #999; font-style: italic; }

/* 推荐商品 */
.msg-products { display: flex; gap: 10px; margin-top: 10px; flex-wrap: wrap; }
.rec-card { display: flex; gap: 10px; align-items: center; background: #fff; border: 1px solid #f0f0f0; border-radius: 8px; padding: 8px; cursor: pointer; transition: all .2s; width: 230px; }
.rec-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.1); border-color: #ff5000; }
.rec-img { width: 64px; height: 64px; flex-shrink: 0; border-radius: 6px; overflow: hidden; }
.rec-img .el-image { width: 100%; height: 100%; }
.rec-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.rec-name { color: #333; font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rec-desc { color: #999; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rec-price { color: #ff5000; font-weight: 700; font-size: 14px; }

/* 输入框 */
.chat-input { padding: 16px; border-top: 1px solid #f0f0f0; }
</style>
