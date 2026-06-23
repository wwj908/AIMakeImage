<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { api, clearAuth, setAuth, state } from './api'

const SESSION_STORAGE_KEY = 'mi_sessions'

const authMode = ref('login')
const activeTab = ref('create')
const showAuthModal = ref(false)
const loading = ref(false)
const message = ref('')
const authError = ref('')
const selectedPrompt = ref('')
const uploadFile = ref(null)
const pastedImageUrl = ref('')
const previewWork = ref(null)
const conversation = ref([])
const conversationSessions = ref(loadSessions())
const currentSessionId = ref(conversationSessions.value[0]?.id || null)
const publicCarouselIndex = ref(0)
const sessionMenuId = ref(null)
let publicCarouselTimer = null

const auth = reactive({ username: '', email: '', account: '', password: '' })
const form = reactive({
  mode: 'generate',
  title: '',
  prompt: '一个穿白色连衣裙的女孩站在海边，傍晚柔光，电影写真，高级色彩',
  publicWork: true,
  ratio: '1:1',
  style: '电影写真'
})

const myWorks = ref([])
const publicWorks = ref([])

const loggedIn = computed(() => Boolean(state.token && state.user))
const accountName = computed(() => (loggedIn.value ? state.user.username : '未登录'))
const displayWorks = computed(() => {
  const source = activeTab.value === 'mine' ? myWorks.value : publicWorks.value
  return source.slice(0, 8)
})
const carouselWorks = computed(() => {
  if (!publicWorks.value.length) return []
  const count = Math.min(4, publicWorks.value.length)
  return Array.from({ length: count }, (_, index) => {
    return publicWorks.value[(publicCarouselIndex.value + index) % publicWorks.value.length]
  })
})
const visibleSessions = computed(() => {
  return [...conversationSessions.value]
    .sort((a, b) => {
      const pinDiff = Number(Boolean(b.pinned)) - Number(Boolean(a.pinned))
      if (pinDiff !== 0) return pinDiff
      return (b.updatedAt || 0) - (a.updatedAt || 0)
    })
    .slice(0, 8)
})

const styleTabs = ['精选', '人像摄影', '艺术', '国风插画', '动漫', '3D 渲染', '商品', '风景']
const ratios = ['1:1', '3:4', '4:3', '16:9']

function nowIso() {
  return new Date().toISOString()
}

function formatMessageTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function cloneData(value) {
  return JSON.parse(JSON.stringify(value))
}

function loadSessions() {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY)
    const sessions = raw ? JSON.parse(raw) : []
    return Array.isArray(sessions) ? sessions : []
  } catch (_) {
    return []
  }
}

function persistSessions() {
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(conversationSessions.value))
}

function summarizeSession(messages) {
  const firstUser = messages.find((item) => item.role === 'user')
  if (!firstUser?.content) {
    return '新对话'
  }
  return firstUser.content.length > 18 ? `${firstUser.content.slice(0, 18)}...` : firstUser.content
}

function ensureSession(forceNew = false) {
  if (!forceNew && currentSessionId.value) {
    const existing = conversationSessions.value.find((item) => item.id === currentSessionId.value)
    if (existing) return existing
  }
  const session = {
    id: `session-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    title: '新对话',
    pinned: false,
    updatedAt: Date.now(),
    messages: []
  }
  conversationSessions.value = [session, ...conversationSessions.value]
  currentSessionId.value = session.id
  persistSessions()
  return session
}

function syncSessionFromConversation() {
  const session = ensureSession()
  const next = {
    ...session,
    title: summarizeSession(conversation.value),
    updatedAt: Date.now(),
    messages: cloneData(conversation.value)
  }
  conversationSessions.value = [
    next,
    ...conversationSessions.value.filter((item) => item.id !== next.id)
  ]
  persistSessions()
}

function openSession(sessionId) {
  const session = conversationSessions.value.find((item) => item.id === sessionId)
  if (!session) return
  currentSessionId.value = session.id
  conversation.value = cloneData(session.messages || [])
  activeTab.value = 'create'
  sessionMenuId.value = null
}

function handleSessionFocusOut(event) {
  const nextTarget = event.relatedTarget
  if (nextTarget && event.currentTarget.contains(nextTarget)) return
  sessionMenuId.value = null
}

function toggleSessionMenu(sessionId) {
  sessionMenuId.value = sessionMenuId.value === sessionId ? null : sessionId
}

function pinSession(sessionId) {
  conversationSessions.value = conversationSessions.value.map((item) => {
    if (item.id !== sessionId) return item
    return { ...item, pinned: !item.pinned, updatedAt: Date.now() }
  })
  persistSessions()
  sessionMenuId.value = null
}

function renameSession(sessionId) {
  const session = conversationSessions.value.find((item) => item.id === sessionId)
  if (!session) return
  const nextTitle = window.prompt('重命名会话', session.title)
  if (nextTitle == null) return
  const trimmed = nextTitle.trim()
  if (!trimmed) {
    toast('名称不能为空')
    return
  }
  conversationSessions.value = conversationSessions.value.map((item) => {
    if (item.id !== sessionId) return item
    return { ...item, title: trimmed, updatedAt: Date.now() }
  })
  persistSessions()
  sessionMenuId.value = null
}

function deleteSession(sessionId) {
  const session = conversationSessions.value.find((item) => item.id === sessionId)
  if (!session) return
  const confirmed = window.confirm(`删除会话“${session.title}”？`)
  if (!confirmed) return
  const nextSessions = conversationSessions.value.filter((item) => item.id !== sessionId)
  conversationSessions.value = nextSessions
  if (currentSessionId.value === sessionId) {
    const fallback = nextSessions[0]
    currentSessionId.value = fallback?.id || null
    conversation.value = cloneData(fallback?.messages || [])
  }
  if (!nextSessions.length) {
    ensureSession(true)
    conversation.value = []
  }
  persistSessions()
  sessionMenuId.value = null
}

function toast(text) {
  message.value = text
  window.setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 2400)
}

function startPublicCarousel() {
  if (publicCarouselTimer) {
    window.clearInterval(publicCarouselTimer)
    publicCarouselTimer = null
  }
  if (publicWorks.value.length <= 4) return
  publicCarouselTimer = window.setInterval(() => {
    publicCarouselIndex.value = (publicCarouselIndex.value + 4) % publicWorks.value.length
  }, 2600)
}

function validateAuthForm() {
  authError.value = ''
  if (authMode.value === 'login') {
    if (!auth.account.trim()) return '请输入用户名或邮箱'
    if (!auth.password) return '请输入密码'
    return ''
  }
  if (!auth.username.trim()) return '请输入用户名'
  if (auth.username.trim().length < 2) return '用户名至少需要 2 个字符'
  if (!auth.email.trim()) return '请输入邮箱'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(auth.email.trim())) return '请输入正确的邮箱地址'
  if (!auth.password) return '请输入密码'
  if (auth.password.length < 6) return '密码至少需要 6 位'
  return ''
}

function setReferenceImage(file, source = '已选择参考图') {
  if (!file || !file.type?.startsWith('image/')) return
  if (pastedImageUrl.value) {
    URL.revokeObjectURL(pastedImageUrl.value)
  }
  uploadFile.value = file
  pastedImageUrl.value = URL.createObjectURL(file)
  form.mode = 'edit'
  toast(source)
}

function clearReferenceImage() {
  if (pastedImageUrl.value) {
    URL.revokeObjectURL(pastedImageUrl.value)
  }
  uploadFile.value = null
  pastedImageUrl.value = ''
  form.mode = 'generate'
}

function handlePasteImage(event) {
  const items = Array.from(event.clipboardData?.items || [])
  const imageItem = items.find((item) => item.type.startsWith('image/'))
  if (!imageItem) return
  const file = imageItem.getAsFile()
  if (!file) return
  event.preventDefault()
  setReferenceImage(file, '已粘贴图片，自动切换到改图模式')
}

function createPendingMessage(promptSnapshot) {
  return {
    id: `assistant-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role: 'assistant',
    status: 'loading',
    createdAt: nowIso(),
    title: form.title || `${form.style} · ${form.ratio}`,
    prompt: promptSnapshot,
    style: form.style,
    ratio: form.ratio,
    mode: form.mode
  }
}

function addConversationPair(promptSnapshot) {
  ensureSession()
  const userEntry = {
    id: `user-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role: 'user',
    createdAt: nowIso(),
    content: promptSnapshot,
    style: form.style,
    ratio: form.ratio,
    mode: form.mode,
    referenceName: uploadFile.value?.name || ''
  }
  const pendingEntry = createPendingMessage(promptSnapshot)
  conversation.value.push(userEntry, pendingEntry)
  return pendingEntry.id
}

function syncCreatedWork(createdWork) {
  if (!createdWork) return
  myWorks.value = [createdWork, ...myWorks.value.filter((work) => work.id !== createdWork.id)]
  if (createdWork.publicWork) {
    publicWorks.value = [createdWork, ...publicWorks.value.filter((work) => work.id !== createdWork.id)]
  }
}

function updateConversationSuccess(messageId, createdWork) {
  conversation.value = conversation.value.map((item) => {
    if (item.id !== messageId) return item
    return {
      ...item,
      status: 'done',
      answeredAt: nowIso(),
      work: createdWork
    }
  })
}

function updateConversationError(messageId, errorText) {
  conversation.value = conversation.value.map((item) => {
    if (item.id !== messageId) return item
    return {
      ...item,
      status: 'error',
      answeredAt: nowIso(),
      error: errorText
    }
  })
}

async function submitAuth() {
  const validationMessage = validateAuthForm()
  if (validationMessage) {
    authError.value = validationMessage
    return
  }
  loading.value = true
  try {
    const res = authMode.value === 'login'
      ? await api.login({ account: auth.account, password: auth.password })
      : await api.register({ username: auth.username, email: auth.email, password: auth.password })
    setAuth(res)
    toast(authMode.value === 'login' ? '欢迎回来' : '注册成功')
    showAuthModal.value = false
    await refresh()
  } catch (error) {
    authError.value = error.message
  } finally {
    loading.value = false
  }
}

async function createArtwork() {
  const promptText = form.prompt.trim()
  if (!loggedIn.value) {
    showAuthModal.value = true
    toast('请先登录')
    return
  }
  if (!promptText) {
    toast('请先输入提示词')
    return
  }
  if (loading.value) return

  const pendingMessageId = addConversationPair(promptText)
  loading.value = true

  try {
    const payload = {
      title: form.title || `${form.style} · ${form.ratio}`,
      prompt: `${promptText}，${form.style}，画幅 ${form.ratio}`,
      publicWork: form.publicWork
    }
    let createdWork
    if (form.mode === 'edit') {
      if (!uploadFile.value) throw new Error('请先上传要修改的图片')
      createdWork = await api.edit(payload, uploadFile.value)
    } else {
      createdWork = await api.generate(payload)
    }
    syncCreatedWork(createdWork)
    updateConversationSuccess(pendingMessageId, createdWork)
    toast('图片已生成')
    await refresh()
    clearReferenceImage()
  } catch (error) {
    updateConversationError(pendingMessageId, error.message)
    toast(error.message)
  } finally {
    loading.value = false
  }
}

async function refresh() {
  try {
    publicWorks.value = (await api.publicWorks()).content || []
    if (publicCarouselIndex.value >= publicWorks.value.length) {
      publicCarouselIndex.value = 0
    }
    startPublicCarousel()
    if (loggedIn.value) {
      myWorks.value = (await api.myWorks()).content || []
    }
  } catch (error) {
    toast(error.message)
  }
}

async function togglePublish(work) {
  try {
    const targetPublic = !work.publicWork
    const updated = await api.publish(work.id, targetPublic)
    Object.assign(work, updated)
    myWorks.value = myWorks.value.map((item) => item.id === updated.id ? { ...item, ...updated } : item)
    publicWorks.value = targetPublic
      ? [updated, ...publicWorks.value.filter((item) => item.id !== updated.id)]
      : publicWorks.value.filter((item) => item.id !== updated.id)
    conversation.value = conversation.value.map((item) => {
      if (!item.work || item.work.id !== work.id) return item
      return {
        ...item,
        work: { ...item.work, ...updated }
      }
    })
    toast(targetPublic ? '已公开发布' : '已取消公开')
  } catch (error) {
    toast(error.message)
  }
}

async function download(work) {
  try {
    await api.addDownload(work.id)
  } catch (_) {
  }
  try {
    const response = await fetch(work.imageUrl)
    if (!response.ok) {
      throw new Error('下载失败')
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = `${work.title || 'make-image'}.png`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(blobUrl)
  } catch (error) {
    toast(error.message || '下载失败')
  }
}

async function copyPrompt(work) {
  const prompt = work.prompt || work.content || ''
  await navigator.clipboard.writeText(prompt)
  selectedPrompt.value = prompt
  form.prompt = prompt
  toast('提示词已复制到输入框')
}

function logout() {
  clearAuth()
  myWorks.value = []
  activeTab.value = 'create'
  toast('已退出登录')
}

function startNewChat() {
  ensureSession(true)
  conversation.value = []
  form.title = ''
  form.prompt = ''
  form.style = '电影写真'
  form.ratio = '1:1'
  form.mode = 'generate'
  form.publicWork = true
  clearReferenceImage()
  activeTab.value = 'create'
}

watch(conversation, () => {
  syncSessionFromConversation()
}, { deep: true })

onMounted(refresh)
onUnmounted(() => {
  if (publicCarouselTimer) {
    window.clearInterval(publicCarouselTimer)
  }
  sessionMenuId.value = null
})

if (!conversationSessions.value.length) {
  ensureSession(true)
}
</script>

<template>
  <main class="doubao-layout">
    <aside class="sidebar">
      <div class="profile">
        <img class="app-logo" src="/image.png" alt="MakeImage AI logo" />
        <div>
          <strong>{{ accountName }}</strong>
          <span>MakeImage AI</span>
        </div>
      </div>

      <button class="new-chat" @click="startNewChat">+ 新对话</button>

      <nav class="side-nav">
        <button :class="{ active: activeTab === 'create' }" @click="activeTab = 'create'">
          <span>✦</span> 图像生成
        </button>
        <button :class="{ active: activeTab === 'gallery' }" @click="activeTab = 'gallery'">
          <span>⌘</span> 公开作品
        </button>
        <button :class="{ active: activeTab === 'mine' }" :disabled="!loggedIn" @click="activeTab = 'mine'">
          <span>▣</span> 我的作品
        </button>
      </nav>

      <div class="recent">
        <p>会话记录</p>
        <div
          v-for="session in visibleSessions"
          :key="session.id"
          class="session-item"
          :class="{ active: currentSessionId === session.id }"
          @focusout="handleSessionFocusOut"
        >
          <button class="session-link" @click="openSession(session.id)">
            <span class="session-title">{{ session.title }}</span>
            <span v-if="session.pinned" class="session-pin">置顶</span>
          </button>
          <button class="session-more" type="button" @click.stop="toggleSessionMenu(session.id)">•••</button>
          <div v-if="sessionMenuId === session.id" class="session-menu">
            <button type="button" @click="pinSession(session.id)">{{ session.pinned ? '取消置顶' : '置顶' }}</button>
            <button type="button" @click="renameSession(session.id)">重命名</button>
            <button type="button" class="danger" @click="deleteSession(session.id)">删除</button>
          </div>
        </div>
        <span v-if="!conversationSessions.length">暂无会话记录</span>
      </div>

      <div class="side-footer">
        <button v-if="loggedIn" @click="logout">退出登录</button>
        <button v-else @click="showAuthModal = true">登录 / 注册</button>
      </div>
    </aside>

    <section class="main-panel">
      <section v-if="activeTab === 'create'" class="image-page">
        <div class="chat-thread" :class="{ compact: conversation.length }">
          <div v-if="!conversation.length" class="chat-empty">
            <div class="showcase-head">
              <div>
                <strong>作品展示</strong>
                <span>浏览公开发布的最新作品</span>
              </div>
              <button type="button" @click="activeTab = 'gallery'">更多作品</button>
            </div>
            <article
              v-for="work in carouselWorks"
              :key="work.id"
              class="hero-work-card"
              @click="previewWork = work"
            >
              <img :src="work.imageUrl" :alt="work.title" />
              <div class="hero-work-mask">
                <strong>{{ work.title }}</strong>
                <span>@{{ work.ownerName }}</span>
              </div>
            </article>
            <div v-if="!carouselWorks.length" class="empty-card">
              <strong>还没有公开作品</strong>
              <span>登录后发送第一条创作请求，这里就会开始展示公开作品轮播。</span>
            </div>
          </div>

          <article
            v-for="item in conversation"
            :key="item.id"
            class="chat-message"
            :class="item.role"
          >
            <div class="message-body">
              <div class="message-meta">
                <strong>{{ item.role === 'assistant' ? 'MakeImage AI' : accountName }}</strong>
                <span v-if="item.style">{{ item.style }} · {{ item.ratio }}</span>
                <span v-if="item.role === 'user' && item.createdAt">{{ formatMessageTime(item.createdAt) }}</span>
                <span v-if="item.role === 'assistant' && (item.answeredAt || item.createdAt)">
                  {{ formatMessageTime(item.answeredAt || item.createdAt) }}
                </span>
              </div>

              <div v-if="item.role === 'user'" class="message-bubble user-bubble">
                <p>{{ item.content }}</p>
                <div class="bubble-tags">
                  <span>{{ item.mode === 'edit' ? '改图模式' : '文生图' }}</span>
                  <span v-if="item.referenceName">{{ item.referenceName }}</span>
                </div>
              </div>

              <div v-else class="assistant-panel">
                <div v-if="item.status === 'loading'" class="message-bubble assistant-bubble loading-bubble">
                  <div class="loading-dots" aria-hidden="true">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                  <div>
                    <strong>正在生成中</strong>
                    <p>AI 正在处理你的画面描述，请稍候。</p>
                  </div>
                </div>

                <div v-else-if="item.status === 'error'" class="message-bubble assistant-bubble error-bubble">
                  <strong>生成失败</strong>
                  <p>{{ item.error }}</p>
                </div>

                <div v-else-if="item.work" class="assistant-result">
                  <div class="result-header">
                    <strong>图片已生成</strong>
                    <span>{{ item.work.publicWork ? '已公开' : '仅自己可见' }}</span>
                  </div>
                  <div class="result-card">
                    <img :src="item.work.imageUrl" :alt="item.work.title" />
                    <div class="result-actions">
                      <button class="icon-pill" @click="previewWork = item.work" title="查看图片">👁</button>
                      <button @click="copyPrompt(item.work)">复制提示词</button>
                      <button @click="download(item.work)">下载</button>
                      <button type="button" @click.stop="togglePublish(item.work)">
                        {{ item.work.publicWork ? '取消公开' : '公开发布' }}
                      </button>
                    </div>
                  </div>
                  <div class="result-info">
                    <strong>{{ item.work.title }}</strong>
                    <span>@{{ item.work.ownerName }} · {{ item.work.downloadCount }} 下载</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="message-avatar">
              <img v-if="item.role === 'assistant'" src="/image.png" alt="AI" />
              <span v-else>{{ loggedIn ? accountName.slice(0, 1) : '我' }}</span>
            </div>
          </article>
        </div>

        <form class="composer" @submit.prevent="createArtwork" @paste="handlePasteImage">
          <div v-if="pastedImageUrl" class="reference-preview">
            <img :src="pastedImageUrl" alt="参考图预览" />
            <span>{{ uploadFile?.name || '粘贴的图片' }}</span>
            <button type="button" @click="clearReferenceImage">移除</button>
          </div>
          <textarea
            v-model="form.prompt"
            rows="2"
            placeholder="描述你想生成的画面，支持直接粘贴图片"
            @paste="handlePasteImage"
          ></textarea>
          <div class="composer-bottom">
            <div class="quick-tools">
              <label class="tool-btn">
                <input type="file" accept="image/*" @change="setReferenceImage($event.target.files[0])" />
                <span>参考图</span>
              </label>
              <button
                v-for="item in ratios"
                :key="item"
                type="button"
                :class="{ active: form.ratio === item }"
                @click="form.ratio = item"
              >
                {{ item }}
              </button>
              <button
                v-for="item in styleTabs"
                :key="`style-${item}`"
                type="button"
                :class="{ active: form.style === item }"
                @click="form.style = item"
              >
                {{ item }}
              </button>
              <button type="button" :class="{ active: form.mode === 'edit' }" @click="form.mode = form.mode === 'edit' ? 'generate' : 'edit'">
                {{ form.mode === 'edit' ? '改图模式' : '文生图' }}
              </button>
              <label class="publish-check">
                <input v-model="form.publicWork" type="checkbox" />
                公开
              </label>
            </div>
            <button class="send-btn" :disabled="loading" :title="loading ? '正在生成中' : '发送生成'">
              {{ loading ? '…' : '↑' }}
            </button>
          </div>
        </form>
      </section>

      <section v-if="activeTab === 'gallery' || activeTab === 'mine'" class="gallery-page">
        <div class="gallery-heading">
          <div>
            <h1>{{ activeTab === 'mine' ? '我的作品' : '公开作品' }}</h1>
            <p>{{ activeTab === 'mine' ? '管理发布状态、下载生成图片' : '查看其他创作者公开的提示词' }}</p>
          </div>
          <button @click="refresh">刷新</button>
        </div>
        <div class="works-grid">
          <article v-for="work in displayWorks" :key="work.id" class="work-card">
            <img :src="work.imageUrl" :alt="work.title" />
            <div class="work-mask">
              <button class="icon-pill" @click="previewWork = work" title="查看图片">👁</button>
              <button @click="copyPrompt(work)">复制提示词</button>
              <button @click="download(work)">下载</button>
              <button v-if="activeTab === 'mine'" type="button" @click.stop="togglePublish(work)">
                {{ work.publicWork ? '取消公开' : '公开发布' }}
              </button>
            </div>
            <div class="work-info">
              <strong>{{ work.title }}</strong>
              <span>@{{ work.ownerName }} · {{ work.downloadCount }} 下载</span>
            </div>
          </article>
          <p v-if="!displayWorks.length" class="empty">{{ activeTab === 'mine' ? '你还没有作品。' : '还没有公开作品。' }}</p>
        </div>
      </section>

      <transition name="toast">
        <div v-if="message" class="toast">{{ message }}</div>
      </transition>
    </section>

    <transition name="modal">
      <div v-if="showAuthModal" class="modal-backdrop" @click.self="showAuthModal = false">
        <form class="login-modal" @submit.prevent="submitAuth">
          <button type="button" class="modal-close" @click="showAuthModal = false">×</button>
          <div class="modal-head">
            <h2>{{ authMode === 'login' ? '登录 MakeImage' : '创建账号' }}</h2>
            <p>{{ authMode === 'login' ? '登录后即可生成、保存和发布作品' : '创建账号后开始你的 AI 图片创作' }}</p>
          </div>
          <div class="auth-switch">
            <button type="button" :class="{ active: authMode === 'login' }" @click="authMode = 'login'; authError = ''">登录</button>
            <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'; authError = ''">注册</button>
          </div>
          <input v-if="authMode === 'register'" v-model="auth.username" placeholder="用户名" />
          <input v-if="authMode === 'register'" v-model="auth.email" placeholder="邮箱" />
          <input v-if="authMode === 'login'" v-model="auth.account" placeholder="用户名或邮箱" />
          <input v-model="auth.password" type="password" placeholder="密码" />
          <p v-if="authError" class="form-error">{{ authError }}</p>
          <button class="modal-submit" :disabled="loading">{{ loading ? '处理中...' : '继续' }}</button>
        </form>
      </div>
    </transition>

    <transition name="modal">
      <div v-if="previewWork" class="modal-backdrop" @click.self="previewWork = null">
        <div class="image-preview-modal">
          <button type="button" class="modal-close" @click="previewWork = null">×</button>
          <img :src="previewWork.imageUrl" :alt="previewWork.title" />
          <div class="preview-meta">
            <strong>{{ previewWork.title }}</strong>
            <span>@{{ previewWork.ownerName }} · {{ previewWork.downloadCount }} 下载</span>
          </div>
        </div>
      </div>
    </transition>
  </main>
</template>
