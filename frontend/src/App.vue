<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { api, clearAuth, setAuth, state } from './api'

const SESSION_STORAGE_KEY = 'mi_sessions'

const authMode = ref('login')
const activeTab = ref('create')
const showAuthModal = ref(false)
const showRatioModal = ref(false)
const loading = ref(false)
const sendingCode = ref(false)
const emailCodeCountdown = ref(0)
const message = ref('')
const authError = ref('')
const selectedPrompt = ref('')
const uploadFile = ref(null)
const pastedImageUrl = ref('')
const previewWork = ref(null)
const previewComments = ref([])
const commentText = ref('')
const commentsLoading = ref(false)
const editingWork = ref(null)
const metadataForm = reactive({ title: '', tags: '' })
const showUploadModal = ref(false)
const uploadArtworkFile = ref(null)
const uploadArtworkPreview = ref('')
const uploadArtworkForm = reactive({
  title: '',
  prompt: '',
  tags: '',
  ratio: '1:1',
  publicWork: true
})
const adminStats = ref(null)
const adminUsers = ref([])
const adminSettings = reactive({})
const adminOpenAiProviders = ref([])
const adminSection = ref('dashboard')
const adminDeploying = ref(false)
const adminDeployResult = ref(null)
const adminDeployJob = ref(null)
let adminDeployTimer = null
const adminDeployForm = reactive({
  sourceUrl: 'https://github.com/wwj908/AIMakeImage/archive/refs/heads/main.zip',
  targetDir: '/opt/aimakeimage',
  restartCommand: 'systemctl restart aimakeimage'
})
const systemConfig = reactive({ name: 'MakeImage AI', logoUrl: '/image.png' })
const conversation = ref([])
const conversationSessions = ref(loadSessions())
const currentSessionId = ref(conversationSessions.value[0]?.id || null)
const publicCarouselIndex = ref(0)
const sessionMenuId = ref(null)
let publicCarouselTimer = null
let syncingSessions = false
let sessionSaveTimer = null
let emailCodeTimer = null

const auth = reactive({ username: '', email: '', account: '', password: '', emailCode: '' })
const form = reactive({
  mode: 'generate',
  title: '',
  prompt: '一个穿白色连衣裙的女孩站在海边，傍晚柔光，电影写真，高级色彩',
  publicWork: true,
  ratio: '1:1',
  style: '电影写真'
})
const galleryFilters = reactive({
  keyword: '',
  author: '全部',
  style: '全部',
  ratio: '全部',
  mode: '全部',
  sort: '最新'
})

const myWorks = ref([])
const publicWorks = ref([])

const loggedIn = computed(() => Boolean(state.token && state.user))
const isAdmin = computed(() => state.user?.role === 'ADMIN')
const accountName = computed(() => (loggedIn.value ? state.user.username : '未登录'))
const displayWorks = computed(() => {
  const source = activeTab.value === 'mine' ? myWorks.value : filteredPublicWorks.value
  return source.slice(0, 8)
})
const filterStyles = computed(() => {
  const values = publicWorks.value.flatMap((work) => {
    const fromTitle = (work.title || '').split(/[·\s]/)[0]
    const fromTags = (work.tags || '').split(',').map((tag) => tag.trim()).filter(Boolean)
    return [fromTitle, ...fromTags].filter(Boolean)
  })
  return ['全部', ...Array.from(new Set([...styleTabs.filter((item) => item !== '精选'), ...values]))]
})
const filterAuthors = computed(() => {
  return ['全部', ...Array.from(new Set(publicWorks.value.map((work) => work.ownerName).filter(Boolean)))]
})
const filteredPublicWorks = computed(() => {
  const keyword = galleryFilters.keyword.trim().toLowerCase()
  const matchesText = (work) => {
    if (!keyword) return true
    return [work.title, work.prompt, work.ownerName, work.tags]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword))
  }
  const matchesStyle = (work) => {
    if (galleryFilters.style === '全部') return true
    return [work.title, work.prompt, work.tags]
      .filter(Boolean)
      .some((value) => String(value).includes(galleryFilters.style))
  }
  const matchesAuthor = (work) => {
    return galleryFilters.author === '全部' || work.ownerName === galleryFilters.author
  }
  const matchesRatio = (work) => {
    if (galleryFilters.ratio === '全部') return true
    return [work.title, work.prompt]
      .filter(Boolean)
      .some((value) => String(value).includes(galleryFilters.ratio))
  }
  const matchesMode = (work) => {
    if (galleryFilters.mode === '全部') return true
    return galleryFilters.mode === '文生图' ? work.mode === 'generate' : work.mode === 'edit'
  }
  const sorted = publicWorks.value
    .filter((work) => matchesText(work) && matchesAuthor(work) && matchesStyle(work) && matchesRatio(work) && matchesMode(work))
    .sort((a, b) => {
      if (galleryFilters.sort === '下载最多') return (b.downloadCount || 0) - (a.downloadCount || 0)
      if (galleryFilters.sort === '点赞最多') return (b.likeCount || 0) - (a.likeCount || 0)
      if (galleryFilters.sort === '收藏最多') return (b.favoriteCount || 0) - (a.favoriteCount || 0)
      if (galleryFilters.sort === '评论最多') return (b.commentCount || 0) - (a.commentCount || 0)
      return new Date(b.createdAt || 0) - new Date(a.createdAt || 0)
    })
  return sorted
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
const ratios = [
  '1:1',
  '3:2',
  '2:3',
  '4:3',
  '3:4',
  '16:9',
  '9:16',
  '21:9',
  '2:1',
  '1:2',
  '5:4',
  '4:5',
  '3:1',
  '1:3'
]

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

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date)
}

function cloneData(value) {
  return JSON.parse(JSON.stringify(value))
}

function displayImageUrl(work) {
  return work?.thumbnailUrl || work?.imageUrl || ''
}

function sessionStorageKey() {
  return state.user?.id ? `${SESSION_STORAGE_KEY}:${state.user.id}` : SESSION_STORAGE_KEY
}

function loadSessions() {
  try {
    const raw = localStorage.getItem(sessionStorageKey())
    const sessions = raw ? JSON.parse(raw) : []
    return Array.isArray(sessions) ? sessions : []
  } catch (_) {
    return []
  }
}

function persistSessions() {
  localStorage.setItem(sessionStorageKey(), JSON.stringify(conversationSessions.value))
}

function parseServerSession(session) {
  let messages = []
  try {
    const parsed = JSON.parse(session.messagesJson || '[]')
    messages = Array.isArray(parsed) ? parsed : []
  } catch (_) {
    messages = []
  }
  return {
    id: session.clientId,
    serverId: session.id,
    title: session.title || '新对话',
    pinned: Boolean(session.pinned),
    updatedAt: Date.parse(session.updatedAt || session.createdAt) || Date.now(),
    messages
  }
}

function sessionPayload(session) {
  return {
    clientId: session.id,
    title: session.title || '新对话',
    pinned: Boolean(session.pinned),
    messagesJson: JSON.stringify(session.messages || [])
  }
}

function replaceSession(updatedSession) {
  conversationSessions.value = conversationSessions.value.map((item) => {
    if (item.id !== updatedSession.id) return item
    return { ...item, ...updatedSession }
  })
  if (!conversationSessions.value.some((item) => item.id === updatedSession.id)) {
    conversationSessions.value = [updatedSession, ...conversationSessions.value]
  }
}

async function saveSessionToServer(session) {
  if (!loggedIn.value || !session) return
  try {
    const saved = await api.saveChatSession(sessionPayload(session))
    const normalized = parseServerSession(saved)
    replaceSession({ ...session, serverId: normalized.serverId, updatedAt: normalized.updatedAt })
    persistSessions()
  } catch (error) {
    toast(error.message)
  }
}

function queueSaveSession(session) {
  if (!loggedIn.value || !session || syncingSessions) return
  if (sessionSaveTimer) window.clearTimeout(sessionSaveTimer)
  sessionSaveTimer = window.setTimeout(() => {
    saveSessionToServer(session)
  }, 280)
}

async function loadChatSessions() {
  if (!loggedIn.value) {
    conversationSessions.value = loadSessions()
    if (!conversationSessions.value.length) ensureSession(true)
    currentSessionId.value = conversationSessions.value[0]?.id || null
    conversation.value = cloneData(conversationSessions.value[0]?.messages || [])
    return
  }
  syncingSessions = true
  try {
    const serverSessions = await api.chatSessions()
    const localSessions = loadSessions()
    if (serverSessions.length) {
      conversationSessions.value = serverSessions.map(parseServerSession)
    } else {
      conversationSessions.value = localSessions.length ? localSessions : []
      await Promise.all(conversationSessions.value.map((session) => saveSessionToServer(session)))
    }
    if (!conversationSessions.value.length) {
      ensureSession(true)
    }
    currentSessionId.value = conversationSessions.value[0]?.id || null
    conversation.value = cloneData(conversationSessions.value[0]?.messages || [])
    persistSessions()
  } catch (error) {
    toast(error.message)
  } finally {
    syncingSessions = false
  }
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
  queueSaveSession(session)
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
  queueSaveSession(next)
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

async function pinSession(sessionId) {
  const session = conversationSessions.value.find((item) => item.id === sessionId)
  if (!session) return
  const nextPinned = !session.pinned
  const nextSession = { ...session, pinned: nextPinned, updatedAt: Date.now() }
  replaceSession(nextSession)
  persistSessions()
  sessionMenuId.value = null
  if (!loggedIn.value) return
  try {
    const saved = await api.pinChatSession(sessionId, nextPinned)
    replaceSession(parseServerSession(saved))
    persistSessions()
  } catch (error) {
    toast(error.message)
  }
}

async function renameSession(sessionId) {
  const session = conversationSessions.value.find((item) => item.id === sessionId)
  if (!session) return
  const nextTitle = window.prompt('重命名会话', session.title)
  if (nextTitle == null) return
  const trimmed = nextTitle.trim()
  if (!trimmed) {
    toast('名称不能为空')
    return
  }
  replaceSession({ ...session, title: trimmed, updatedAt: Date.now() })
  persistSessions()
  sessionMenuId.value = null
  if (!loggedIn.value) return
  try {
    const saved = await api.renameChatSession(sessionId, trimmed)
    replaceSession(parseServerSession(saved))
    persistSessions()
  } catch (error) {
    toast(error.message)
  }
}

async function deleteSession(sessionId) {
  const session = conversationSessions.value.find((item) => item.id === sessionId)
  if (!session) return
  const confirmed = window.confirm(`删除会话“${session.title}”？`)
  if (!confirmed) return
  if (loggedIn.value) {
    try {
      await api.deleteChatSession(sessionId)
    } catch (error) {
      toast(error.message)
      return
    }
  }
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
  if (!auth.emailCode.trim()) return '请输入邮箱验证码'
  if (!auth.password) return '请输入密码'
  if (auth.password.length < 6) return '密码至少需要 6 位'
  return ''
}

async function sendRegisterCode() {
  if (authMode.value !== 'register') return
  const email = auth.email.trim()
  if (!email) {
    authError.value = '请输入邮箱'
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    authError.value = '请输入正确的邮箱地址'
    return
  }
  sendingCode.value = true
  authError.value = ''
  try {
    await api.sendEmailCode(email)
    emailCodeCountdown.value = 60
    toast('验证码已发送到邮箱')
    if (emailCodeTimer) window.clearInterval(emailCodeTimer)
    emailCodeTimer = window.setInterval(() => {
      emailCodeCountdown.value -= 1
      if (emailCodeCountdown.value <= 0) {
        window.clearInterval(emailCodeTimer)
        emailCodeTimer = null
        emailCodeCountdown.value = 0
      }
    }, 1000)
  } catch (error) {
    authError.value = error.message
  } finally {
    sendingCode.value = false
  }
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

function setUploadArtworkImage(file) {
  if (!file || !file.type?.startsWith('image/')) {
    toast('请选择图片文件')
    return
  }
  if (uploadArtworkPreview.value) {
    URL.revokeObjectURL(uploadArtworkPreview.value)
  }
  uploadArtworkFile.value = file
  uploadArtworkPreview.value = URL.createObjectURL(file)
}

function resetUploadArtworkForm() {
  if (uploadArtworkPreview.value) {
    URL.revokeObjectURL(uploadArtworkPreview.value)
  }
  uploadArtworkFile.value = null
  uploadArtworkPreview.value = ''
  uploadArtworkForm.title = ''
  uploadArtworkForm.prompt = ''
  uploadArtworkForm.tags = ''
  uploadArtworkForm.ratio = '1:1'
  uploadArtworkForm.publicWork = true
}

function openUploadArtworkModal() {
  if (!requireLogin()) return
  resetUploadArtworkForm()
  showUploadModal.value = true
}

function closeUploadArtworkModal() {
  showUploadModal.value = false
  resetUploadArtworkForm()
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

function handlePromptKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  createArtwork()
}

function createPendingMessage(promptSnapshot) {
  return {
    id: `assistant-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role: 'assistant',
    status: 'loading',
    createdAt: nowIso(),
    title: form.title || `${form.style} 路 ${form.ratio}`,
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
  syncArtworkState(createdWork)
}

function syncArtworkState(updatedWork) {
  if (!updatedWork) return
  const isMine = state.user?.id === updatedWork.ownerId
  if (isMine) {
    myWorks.value = myWorks.value.some((work) => work.id === updatedWork.id)
      ? myWorks.value.map((work) => work.id === updatedWork.id ? { ...work, ...updatedWork } : work)
      : [updatedWork, ...myWorks.value]
  } else {
    myWorks.value = myWorks.value.map((work) => work.id === updatedWork.id ? { ...work, ...updatedWork } : work)
  }
  publicWorks.value = updatedWork.publicWork
    ? [updatedWork, ...publicWorks.value.filter((work) => work.id !== updatedWork.id)]
    : publicWorks.value.filter((work) => work.id !== updatedWork.id)
  conversation.value = conversation.value.map((item) => {
    if (!item.work || item.work.id !== updatedWork.id) return item
    return {
      ...item,
      work: { ...item.work, ...updatedWork }
    }
  })
  if (previewWork.value?.id === updatedWork.id) {
    previewWork.value = { ...previewWork.value, ...updatedWork }
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
      : await api.register({ username: auth.username, email: auth.email, password: auth.password, emailCode: auth.emailCode })
    setAuth(res)
    toast(authMode.value === 'login' ? '欢迎回来' : '注册成功')
    showAuthModal.value = false
    await loadChatSessions()
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
  form.prompt = ''
  loading.value = true

  try {
    const payload = {
      title: form.title || `${form.style} 路 ${form.ratio}`,
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
    if (isAdmin.value) {
      await loadAdminData()
    }
  } catch (error) {
    toast(error.message)
  }
}

async function loadSystemConfig() {
  try {
    const settings = await api.publicSystem()
    applySystemConfig(settings)
  } catch (_) {
  }
}

function applySystemConfig(settings) {
  systemConfig.name = settings['system.name'] || 'MakeImage AI'
  systemConfig.logoUrl = settings['system.logoUrl'] || '/image.png'
  document.title = systemConfig.name
  let icon = document.querySelector("link[rel='icon']")
  if (!icon) {
    icon = document.createElement('link')
    icon.rel = 'icon'
    document.head.appendChild(icon)
  }
  icon.href = systemConfig.logoUrl
}

async function loadAdminData() {
  if (!isAdmin.value) return
  const [stats, users, settings, providers] = await Promise.all([
    api.adminStats(),
    api.adminUsers(),
    api.adminSettings(),
    api.adminOpenAiProviders()
  ])
  adminStats.value = stats
  adminUsers.value = users
  Object.keys(adminSettings).forEach((key) => delete adminSettings[key])
  Object.assign(adminSettings, settings)
  adminOpenAiProviders.value = normalizeProviderSort(providers)
}

async function saveAdminSettings() {
  try {
    const updated = await api.updateAdminSettings({ ...adminSettings })
    Object.keys(adminSettings).forEach((key) => delete adminSettings[key])
    Object.assign(adminSettings, updated)
    applySystemConfig(updated)
    toast('系统配置已保存')
  } catch (error) {
    toast(error.message)
  }
}

function normalizeProviderSort(providers) {
  return (providers || []).map((provider, index) => ({
    id: provider.id || null,
    name: provider.name || `OpenAI 渠道 ${index + 1}`,
    baseUrl: provider.baseUrl || '',
    apiKey: provider.apiKey || '',
    model: provider.model || 'gpt-image-2',
    enabled: Boolean(provider.enabled),
    sortOrder: index + 1
  }))
}

function normalizeDeployJob(job) {
  if (!job) return null
  return {
    ...job,
    steps: Array.isArray(job.steps) ? job.steps : []
  }
}

function applyRuntimeRecommendation(job) {
  const recommended = job?.runtime?.recommendedCommand
  if (recommended && !adminDeployForm.restartCommand.trim()) {
    adminDeployForm.restartCommand = recommended
  }
}

function stopDeployPolling() {
  if (adminDeployTimer) {
    window.clearInterval(adminDeployTimer)
    adminDeployTimer = null
  }
}

async function syncDeployJob(jobId) {
  if (!jobId) return
  try {
    const job = normalizeDeployJob(await api.adminDeployJob(jobId))
    adminDeployJob.value = job
    applyRuntimeRecommendation(job)
    if (job?.status === 'SUCCESS') {
      adminDeployResult.value = job.result || adminDeployResult.value
      stopDeployPolling()
      toast('部署已完成')
    }
    if (job?.status === 'FAILED') {
      adminDeployResult.value = null
      stopDeployPolling()
      toast(job.message || '部署失败')
    }
  } catch (error) {
    toast(error.message)
    stopDeployPolling()
  }
}

function startDeployPolling(jobId) {
  stopDeployPolling()
  if (!jobId) return
  adminDeployTimer = window.setInterval(() => {
    syncDeployJob(jobId)
  }, 1200)
}

function deployStatusText(status) {
  return {
    PENDING: '等待中',
    RUNNING: '进行中',
    SUCCESS: '完成',
    FAILED: '失败'
  }[status] || status || '未知'
}

function stepStatusText(status) {
  return {
    PENDING: '未开始',
    RUNNING: '进行中',
    SUCCESS: '已完成',
    SKIPPED: '已跳过',
    FAILED: '失败'
  }[status] || status || '未知'
}

function runtimeText(runtime) {
  if (!runtime) return '自动识别中'
  return `${runtime.runtime} · ${runtime.description}`
}

function addOpenAiProvider() {
  adminOpenAiProviders.value.push({
    id: null,
    name: `OpenAI 渠道 ${adminOpenAiProviders.value.length + 1}`,
    baseUrl: '',
    apiKey: '',
    model: 'gpt-image-2',
    enabled: true,
    sortOrder: adminOpenAiProviders.value.length + 1
  })
}

function removeOpenAiProvider(index) {
  adminOpenAiProviders.value.splice(index, 1)
  adminOpenAiProviders.value = normalizeProviderSort(adminOpenAiProviders.value)
}

function moveOpenAiProvider(index, direction) {
  const target = index + direction
  if (target < 0 || target >= adminOpenAiProviders.value.length) return
  const providers = [...adminOpenAiProviders.value]
  const [item] = providers.splice(index, 1)
  providers.splice(target, 0, item)
  adminOpenAiProviders.value = normalizeProviderSort(providers)
}

async function saveOpenAiProviders() {
  try {
    const providers = normalizeProviderSort(adminOpenAiProviders.value)
    const updated = await api.updateAdminOpenAiProviders(providers)
    adminOpenAiProviders.value = normalizeProviderSort(updated)
    toast('OpenAI 渠道已保存')
  } catch (error) {
    toast(error.message)
  }
}

async function uploadSystemLogo(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!file.type?.startsWith('image/')) {
    toast('请选择图片文件')
    return
  }
  try {
    const updated = await api.uploadSystemLogo(file)
    Object.keys(adminSettings).forEach((key) => delete adminSettings[key])
    Object.assign(adminSettings, updated)
    applySystemConfig(updated)
    toast('系统 Logo 已更新')
  } catch (error) {
    toast(error.message)
  }
}

async function changeUserRole(user, role) {
  try {
    const updated = await api.updateUserRole(user.id, role)
    adminUsers.value = adminUsers.value.map((item) => item.id === updated.id ? updated : item)
    toast('用户角色已更新')
  } catch (error) {
    toast(error.message)
  }
}

async function deployFromGitHub() {
  const sourceUrl = adminDeployForm.sourceUrl.trim()
  const targetDir = adminDeployForm.targetDir.trim()
  if (!sourceUrl) {
    toast('请输入 GitHub 压缩包地址')
    return
  }
  if (!targetDir) {
    toast('请输入部署目录')
    return
  }
  adminDeploying.value = true
  adminDeployResult.value = null
  adminDeployJob.value = null
  try {
    const job = normalizeDeployJob(await api.adminDeploy({
      sourceUrl,
      targetDir,
      restartCommand: adminDeployForm.restartCommand.trim()
    }))
    adminDeployJob.value = job
    applyRuntimeRecommendation(job)
    if (job?.id) {
      startDeployPolling(job.id)
      await syncDeployJob(job.id)
    }
    if (job?.status !== 'SUCCESS') {
      toast('部署已开始')
    }
  } catch (error) {
    toast(error.message)
  } finally {
    adminDeploying.value = false
  }
}

async function togglePublish(work) {
  try {
    const targetPublic = !work.publicWork
    const updated = await api.publish(work.id, targetPublic)
    Object.assign(work, updated)
    syncArtworkState(updated)
    toast(targetPublic ? '已公开发布' : '已取消公开')
  } catch (error) {
    toast(error.message)
  }
}

function openMetadataEditor(work) {
  editingWork.value = work
  metadataForm.title = work.title || ''
  metadataForm.tags = work.tags || ''
}

async function saveMetadata() {
  if (!editingWork.value) return
  const title = metadataForm.title.trim()
  if (!title) {
    toast('请输入作品标题')
    return
  }
  try {
    const updated = await api.updateMetadata(editingWork.value.id, {
      title,
      tags: metadataForm.tags.trim()
    })
    syncArtworkState(updated)
    editingWork.value = null
    toast('作品信息已保存')
  } catch (error) {
    toast(error.message)
  }
}

async function submitUploadArtwork() {
  if (!uploadArtworkFile.value) {
    toast('请先上传作品图片')
    return
  }
  const title = uploadArtworkForm.title.trim()
  const prompt = uploadArtworkForm.prompt.trim()
  if (!title) {
    toast('请输入作品标题')
    return
  }
  if (!prompt) {
    toast('请输入作品提示词或描述')
    return
  }
  loading.value = true
  try {
    const created = await api.uploadArtwork({
      title: `${title} · ${uploadArtworkForm.ratio}`,
      prompt,
      tags: uploadArtworkForm.tags.trim(),
      ratio: uploadArtworkForm.ratio,
      publicWork: uploadArtworkForm.publicWork
    }, uploadArtworkFile.value)
    syncArtworkState(created)
    showUploadModal.value = false
    resetUploadArtworkForm()
    toast('作品已发布')
  } catch (error) {
    toast(error.message)
  } finally {
    loading.value = false
  }
}

function requireLogin() {
  if (loggedIn.value) return true
  showAuthModal.value = true
  toast('请先登录')
  return false
}

async function toggleLike(work) {
  if (!requireLogin()) return
  try {
    const updated = await api.like(work.id)
    Object.assign(work, updated)
    syncArtworkState(updated)
  } catch (error) {
    toast(error.message)
  }
}

async function toggleFavorite(work) {
  if (!requireLogin()) return
  try {
    const updated = await api.favorite(work.id)
    Object.assign(work, updated)
    syncArtworkState(updated)
  } catch (error) {
    toast(error.message)
  }
}

async function openPreview(work) {
  previewWork.value = work
  commentText.value = ''
  await loadComments(work.id)
}

function showAuthorWorks(work) {
  if (!work?.ownerName) return
  galleryFilters.author = work.ownerName
  galleryFilters.keyword = ''
  activeTab.value = 'gallery'
  previewWork.value = null
  toast(`正在查看 ${work.ownerName} 的公开作品`)
}

async function loadComments(workId) {
  commentsLoading.value = true
  try {
    previewComments.value = await api.comments(workId)
  } catch (error) {
    toast(error.message)
  } finally {
    commentsLoading.value = false
  }
}

async function submitComment() {
  if (!previewWork.value || !requireLogin()) return
  const content = commentText.value.trim()
  if (!content) {
    toast('请输入评论内容')
    return
  }
  try {
    const created = await api.addComment(previewWork.value.id, content)
    previewComments.value = [...previewComments.value, created]
    commentText.value = ''
    const updated = {
      ...previewWork.value,
      commentCount: (previewWork.value.commentCount || 0) + 1
    }
    syncArtworkState(updated)
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
  adminStats.value = null
  adminUsers.value = []
  adminDeployResult.value = null
  adminDeployJob.value = null
  stopDeployPolling()
  Object.keys(adminSettings).forEach((key) => delete adminSettings[key])
  conversationSessions.value = loadSessions()
  currentSessionId.value = conversationSessions.value[0]?.id || null
  conversation.value = cloneData(conversationSessions.value[0]?.messages || [])
  if (!conversationSessions.value.length) ensureSession(true)
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

onMounted(async () => {
  await loadSystemConfig()
  await loadChatSessions()
  await refresh()
})
onUnmounted(() => {
  if (publicCarouselTimer) {
    window.clearInterval(publicCarouselTimer)
  }
  if (sessionSaveTimer) {
    window.clearTimeout(sessionSaveTimer)
  }
  if (emailCodeTimer) {
    window.clearInterval(emailCodeTimer)
  }
  stopDeployPolling()
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
        <img class="app-logo" :src="systemConfig.logoUrl" alt="系统 logo" />
        <div>
          <strong>{{ accountName }}</strong>
          <span>{{ systemConfig.name }}</span>
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
        <button v-if="isAdmin" :class="{ active: activeTab === 'admin' }" @click="activeTab = 'admin'; loadAdminData()">
          <span>⚙</span> 管理后台
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
              @click="openPreview(work)"
            >
              <img :src="displayImageUrl(work)" :alt="work.title" loading="lazy" decoding="async" />
              <div class="hero-work-mask">
                <strong>{{ work.title }}</strong>
                <span>@{{ work.ownerName }}</span>
              </div>
            </article>
            <div v-if="!carouselWorks.length" class="empty-card">
              <strong>还没有公开作品</strong>
              <span>登录后发送第一条创作请求，这里就会开始展示公开作品。</span>
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
                <strong>{{ item.role === 'assistant' ? systemConfig.name : accountName }}</strong>
                <span v-if="item.style">{{ item.style }} 路 {{ item.ratio }}</span>
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
                    <img :src="displayImageUrl(item.work)" :alt="item.work.title" loading="lazy" decoding="async" />
                    <div class="result-actions">
                      <button class="icon-pill" @click="openPreview(item.work)" title="查看图片">👁</button>
                      <button :class="{ active: item.work.liked }" @click="toggleLike(item.work)">赞 {{ item.work.likeCount || 0 }}</button>
                      <button :class="{ active: item.work.favorited }" @click="toggleFavorite(item.work)">收藏 {{ item.work.favoriteCount || 0 }}</button>
                      <button @click="openPreview(item.work)">评论 {{ item.work.commentCount || 0 }}</button>
                      <button @click="copyPrompt(item.work)">复制提示词</button>
                      <button @click="download(item.work)">下载</button>
                      <button type="button" @click.stop="togglePublish(item.work)">
                        {{ item.work.publicWork ? '取消公开' : '公开发布' }}
                      </button>
                    </div>
                  </div>
                  <div class="result-info">
                    <strong>{{ item.work.title }}</strong>
                    <span>@{{ item.work.ownerName }} · {{ item.work.downloadCount }} 下载 · {{ item.work.likeCount || 0 }} 赞 · {{ item.work.favoriteCount || 0 }} 收藏 · {{ item.work.commentCount || 0 }} 评论</span>
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
            @keydown="handlePromptKeydown"
          ></textarea>
          <div class="composer-bottom">
            <div class="quick-tools">
              <label class="tool-btn">
                <input type="file" accept="image/*" @change="setReferenceImage($event.target.files[0])" />
                <span>参考图</span>
              </label>
              <button type="button" class="ratio-trigger" @click="showRatioModal = true">
                <span>比例大小</span>
                <strong>{{ form.ratio }}</strong>
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
          <div class="gallery-heading-actions">
            <button v-if="activeTab === 'mine'" class="primary" @click="openUploadArtworkModal">发布作品</button>
            <button @click="refresh">刷新</button>
          </div>
        </div>
        <div v-if="activeTab === 'gallery'" class="gallery-filters">
          <label class="gallery-search">
            <span>搜索</span>
            <input v-model="galleryFilters.keyword" placeholder="标题、提示词、作者、标签" />
          </label>
          <label>
            <span>作者</span>
            <select v-model="galleryFilters.author">
              <option v-for="item in filterAuthors" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <label>
            <span>分类</span>
            <select v-model="galleryFilters.style">
              <option v-for="item in filterStyles" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <label>
            <span>画幅</span>
            <select v-model="galleryFilters.ratio">
              <option value="全部">全部</option>
              <option v-for="item in ratios" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <label>
            <span>模式</span>
            <select v-model="galleryFilters.mode">
              <option value="全部">全部</option>
              <option value="文生图">文生图</option>
              <option value="改图模式">改图模式</option>
            </select>
          </label>
          <label>
            <span>排序</span>
            <select v-model="galleryFilters.sort">
              <option value="最新">最新</option>
              <option value="下载最多">下载最多</option>
              <option value="点赞最多">点赞最多</option>
              <option value="收藏最多">收藏最多</option>
              <option value="评论最多">评论最多</option>
            </select>
          </label>
          <button
            type="button"
            @click="galleryFilters.keyword = ''; galleryFilters.author = '全部'; galleryFilters.style = '全部'; galleryFilters.ratio = '全部'; galleryFilters.mode = '全部'; galleryFilters.sort = '最新'"
          >
            清空
          </button>
        </div>
        <div class="works-grid">
          <article v-for="work in displayWorks" :key="work.id" class="work-card">
            <img :src="displayImageUrl(work)" :alt="work.title" loading="lazy" decoding="async" />
            <div class="work-mask">
              <button class="icon-pill" @click="openPreview(work)" title="查看图片">👁</button>
              <button :class="{ active: work.liked }" @click="toggleLike(work)">赞 {{ work.likeCount || 0 }}</button>
              <button :class="{ active: work.favorited }" @click="toggleFavorite(work)">收藏 {{ work.favoriteCount || 0 }}</button>
              <button @click="openPreview(work)">评论 {{ work.commentCount || 0 }}</button>
              <button @click="copyPrompt(work)">复制提示词</button>
              <button @click="download(work)">下载</button>
              <button v-if="activeTab === 'mine'" type="button" @click.stop="openMetadataEditor(work)">编辑信息</button>
              <button v-if="activeTab === 'mine'" type="button" @click.stop="togglePublish(work)">
                {{ work.publicWork ? '取消公开' : '公开发布' }}
              </button>
            </div>
            <div class="work-info">
              <strong>{{ work.title }}</strong>
              <div v-if="work.tags" class="tag-list">
                <span v-for="tag in work.tags.split(',').filter(Boolean)" :key="tag">{{ tag.trim() }}</span>
              </div>
              <span>@{{ work.ownerName }} · {{ work.downloadCount }} 下载 · {{ work.likeCount || 0 }} 赞 · {{ work.favoriteCount || 0 }} 收藏 · {{ work.commentCount || 0 }} 评论</span>
            </div>
          </article>
          <p v-if="!displayWorks.length" class="empty">{{ activeTab === 'mine' ? '你还没有作品。' : '还没有公开作品。' }}</p>
        </div>
      </section>

      <section v-if="activeTab === 'admin' && isAdmin" class="admin-page">
        <div class="gallery-heading">
          <div>
            <h1>管理后台</h1>
            <p>查看数据统计，管理用户和系统配置</p>
          </div>
          <button @click="loadAdminData">刷新</button>
        </div>

        <div class="admin-section-nav">
          <button type="button" :class="{ active: adminSection === 'dashboard' }" @click="adminSection = 'dashboard'">数据面板</button>
          <button type="button" :class="{ active: adminSection === 'settings' }" @click="adminSection = 'settings'">系统配置</button>
          <button type="button" :class="{ active: adminSection === 'providers' }" @click="adminSection = 'providers'">OpenAI 渠道</button>
          <button type="button" :class="{ active: adminSection === 'deploy' }" @click="adminSection = 'deploy'">一键部署</button>
          <button type="button" :class="{ active: adminSection === 'users' }" @click="adminSection = 'users'">用户管理</button>
        </div>

        <div v-if="adminSection === 'dashboard'" class="admin-stats">
          <article><span>用户数</span><strong>{{ adminStats?.userCount || 0 }}</strong></article>
          <article><span>作品数</span><strong>{{ adminStats?.artworkCount || 0 }}</strong></article>
          <article><span>公开作品</span><strong>{{ adminStats?.publicArtworkCount || 0 }}</strong></article>
          <article><span>点赞</span><strong>{{ adminStats?.likeCount || 0 }}</strong></article>
          <article><span>收藏</span><strong>{{ adminStats?.favoriteCount || 0 }}</strong></article>
          <article><span>评论</span><strong>{{ adminStats?.commentCount || 0 }}</strong></article>
          <article><span>下载</span><strong>{{ adminStats?.downloadCount || 0 }}</strong></article>
        </div>

        <div v-if="adminSection === 'dashboard'" class="admin-dashboard-summary">
          <section class="admin-panel">
            <div class="admin-panel-head">
              <div>
                <h2>数据面板</h2>
                <p>这里集中查看当前平台的用户、作品与互动数据概览</p>
              </div>
            </div>
            <div class="admin-overview-grid">
              <article>
                <span>当前系统</span>
                <strong>{{ systemConfig.name }}</strong>
              </article>
              <article>
                <span>启用渠道数</span>
                <strong>{{ adminOpenAiProviders.filter((item) => item.enabled).length }}</strong>
              </article>
              <article>
                <span>管理员数</span>
                <strong>{{ adminUsers.filter((item) => item.role === 'ADMIN').length }}</strong>
              </article>
              <article>
                <span>普通用户数</span>
                <strong>{{ adminUsers.filter((item) => item.role === 'USER').length }}</strong>
              </article>
            </div>
          </section>
        </div>

        <div v-if="adminSection === 'settings'" class="admin-grid admin-grid-single">
          <section class="admin-panel">
            <div class="admin-panel-head">
              <div>
                <h2>系统配置</h2>
                <p>配置会存入数据库，敏感项留空或保持掩码不会覆盖原值</p>
              </div>
              <button @click="saveAdminSettings">保存配置</button>
            </div>
            <div class="settings-form">
              <label><span>系统名</span><input v-model="adminSettings['system.name']" /></label>
              <label class="logo-setting">
                <span>系统 Logo</span>
                <div class="logo-upload-row">
                  <label class="logo-upload">
                    <input type="file" accept="image/*" @change="uploadSystemLogo" />
                    <img :src="adminSettings['system.logoUrl'] || systemConfig.logoUrl" alt="系统 Logo" />
                  </label>
                  <input v-model="adminSettings['system.logoUrl']" placeholder="点击图片上传，或手动填写 Logo URL" />
                </div>
              </label>
              <label><span>QQ 邮箱账号</span><input v-model="adminSettings['mail.username']" /></label>
              <label><span>QQ 邮箱授权码</span><input v-model="adminSettings['mail.password']" type="password" /></label>
            </div>
          </section>
        </div>

        <div v-if="adminSection === 'providers'" class="admin-grid admin-grid-single">
          <section class="admin-panel openai-panel">
            <div class="admin-panel-head">
              <div>
                <h2>OpenAI 渠道</h2>
                <p>可配置多个 Base URL 和 API Key，生成时优先使用启用且排序靠前的渠道</p>
              </div>
              <div class="admin-actions">
                <button type="button" @click="addOpenAiProvider">新增渠道</button>
                <button type="button" @click="saveOpenAiProviders">保存渠道</button>
              </div>
            </div>
            <div class="provider-list">
              <article v-for="(provider, index) in adminOpenAiProviders" :key="provider.id || index" class="provider-card">
                <div class="provider-card-head">
                  <strong>#{{ index + 1 }} {{ provider.name || 'OpenAI 渠道' }}</strong>
                  <label class="provider-switch">
                    <input type="checkbox" v-model="provider.enabled" />
                    <span>{{ provider.enabled ? '启用' : '禁用' }}</span>
                  </label>
                </div>
                <div class="provider-fields">
                  <label><span>渠道名称</span><input v-model="provider.name" placeholder="例如：瑞星图" /></label>
                  <label><span>模型</span><input v-model="provider.model" placeholder="例如：gpt-image-2" /></label>
                  <label class="wide"><span>OpenAI Base URL</span><input v-model="provider.baseUrl" placeholder="https://api.example.com" /></label>
                  <label class="wide"><span>OpenAI API Key</span><input v-model="provider.apiKey" type="password" placeholder="留空或保持掩码则不修改" /></label>
                </div>
                <div class="provider-actions">
                  <button type="button" :disabled="index === 0" @click="moveOpenAiProvider(index, -1)">上移</button>
                  <button type="button" :disabled="index === adminOpenAiProviders.length - 1" @click="moveOpenAiProvider(index, 1)">下移</button>
                  <button type="button" class="danger" @click="removeOpenAiProvider(index)">删除</button>
                </div>
              </article>
              <p v-if="!adminOpenAiProviders.length" class="empty">还没有 OpenAI 渠道，点击“新增渠道”添加。</p>
            </div>
          </section>
        </div>

        <div v-if="adminSection === 'users'" class="admin-grid admin-grid-single">
          <section class="admin-panel">
            <div class="admin-panel-head">
              <div>
                <h2>用户管理</h2>
                <p>设置用户角色，管理员入口仅对 ADMIN 可见</p>
              </div>
            </div>
            <div class="admin-user-list">
              <article v-for="user in adminUsers" :key="user.id">
                <div>
                  <strong>{{ user.username }}</strong>
                  <span>{{ user.email }}</span>
                </div>
                <select :value="user.role" @change="changeUserRole(user, $event.target.value)">
                  <option value="USER">USER</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </article>
            </div>
          </section>
        </div>

        <div v-if="adminSection === 'deploy'" class="admin-grid admin-grid-single">
          <section class="admin-panel">
            <div class="admin-panel-head">
              <div>
                <h2>GitHub 一键部署</h2>
                <p>从 GitHub 下载 zip 包，覆盖更新 `backend`、`frontend`、`deploy`、`docs` 目录，并可选执行重启命令</p>
              </div>
              <button :disabled="adminDeploying" @click="deployFromGitHub">
                {{ adminDeploying ? '部署中...' : '开始部署' }}
              </button>
            </div>
            <div class="deploy-form">
              <label>
                <span>GitHub ZIP 地址</span>
                <input v-model="adminDeployForm.sourceUrl" placeholder="https://github.com/owner/repo/archive/refs/heads/main.zip" />
              </label>
              <label>
                <span>部署目录</span>
                <input v-model="adminDeployForm.targetDir" placeholder="/opt/aimakeimage" />
              </label>
              <label class="wide">
                <span>重启命令（可选）</span>
                <input v-model="adminDeployForm.restartCommand" placeholder="systemctl restart aimakeimage" />
              </label>
            </div>
            <div class="deploy-notes">
              <p>仅允许 GitHub 地址，且目标目录限制在 `/opt/aimakeimage` 或 `/www/aimakeimage` 之下。</p>
              <p>运行时数据目录如 `storage` 不会被清空，但部署目录下同名发布目录会被覆盖。</p>
            </div>
            <div v-if="adminDeployJob?.runtime" class="deploy-runtime">
              <article>
                <span>当前环境</span>
                <strong>{{ runtimeText(adminDeployJob.runtime) }}</strong>
              </article>
              <article>
                <span>推荐重启命令</span>
                <strong>{{ adminDeployJob.runtime.recommendedCommand || '无' }}</strong>
              </article>
            </div>
            <div v-if="adminDeployJob" class="deploy-progress">
              <div class="deploy-progress-head">
                <div>
                  <strong>部署进度</strong>
                  <span>{{ deployStatusText(adminDeployJob.status) }} · {{ adminDeployJob.message }}</span>
                </div>
                <b>{{ adminDeployJob.progress }}%</b>
              </div>
              <div class="deploy-progress-bar">
                <i :style="{ width: `${adminDeployJob.progress}%` }"></i>
              </div>
              <div class="deploy-step-list">
                <article v-for="step in adminDeployJob.steps" :key="step.key" :class="step.status.toLowerCase()">
                  <div>
                    <strong>{{ step.name }}</strong>
                    <span>{{ stepStatusText(step.status) }}</span>
                  </div>
                  <p>{{ step.message || '等待执行' }}</p>
                </article>
              </div>
            </div>
            <div v-if="adminDeployResult" class="deploy-result">
              <article>
                <span>来源地址</span>
                <strong>{{ adminDeployResult.sourceUrl }}</strong>
              </article>
              <article>
                <span>部署目录</span>
                <strong>{{ adminDeployResult.targetDir }}</strong>
              </article>
              <article>
                <span>下载大小</span>
                <strong>{{ Math.max(1, Math.round((adminDeployResult.downloadedBytes || 0) / 1024)) }} KB</strong>
              </article>
              <article>
                <span>解压文件数</span>
                <strong>{{ adminDeployResult.extractedFiles || 0 }}</strong>
              </article>
              <article>
                <span>识别发布目录</span>
                <strong>{{ adminDeployResult.usedNestedRelease ? '是' : '否' }}</strong>
              </article>
              <article>
                <span>重启结果</span>
                <strong>{{ adminDeployResult.restarted ? adminDeployResult.restartMessage || '已执行' : '未执行' }}</strong>
              </article>
            </div>
          </section>
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
            <h2>{{ authMode === 'login' ? `登录 ${systemConfig.name}` : `注册 ${systemConfig.name}` }}</h2>
            <p>{{ authMode === 'login' ? '登录后即可生成、保存和发布作品' : '创建账号后开始你的 AI 图片创作' }}</p>
          </div>
          <div class="auth-switch">
            <button type="button" :class="{ active: authMode === 'login' }" @click="authMode = 'login'; authError = ''">登录</button>
            <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'; authError = ''">注册</button>
          </div>
          <input v-if="authMode === 'register'" v-model="auth.username" placeholder="用户名" />
          <input v-if="authMode === 'register'" v-model="auth.email" placeholder="邮箱" />
          <div v-if="authMode === 'register'" class="auth-code-row">
            <input v-model="auth.emailCode" placeholder="邮箱验证码" />
            <button type="button" :disabled="sendingCode || emailCodeCountdown > 0" @click="sendRegisterCode">
              {{ sendingCode ? '发送中...' : (emailCodeCountdown > 0 ? `${emailCodeCountdown}s` : '发送验证码') }}
            </button>
          </div>
          <input v-if="authMode === 'login'" v-model="auth.account" placeholder="用户名或邮箱" />
          <input v-model="auth.password" type="password" placeholder="密码" />
          <p v-if="authError" class="form-error">{{ authError }}</p>
          <button class="modal-submit" :disabled="loading">{{ loading ? '处理中...' : '继续' }}</button>
        </form>
      </div>
    </transition>

    <transition name="modal">
      <div v-if="showRatioModal" class="modal-backdrop" @click.self="showRatioModal = false">
        <div class="ratio-modal">
          <button type="button" class="modal-close" @click="showRatioModal = false">×</button>
          <div class="modal-head">
            <h2>比例大小</h2>
            <p>当前：{{ form.ratio }}</p>
          </div>
          <div class="ratio-grid">
            <button
              v-for="item in ratios"
              :key="item"
              type="button"
              :class="{ active: form.ratio === item }"
              @click="form.ratio = item; showRatioModal = false"
            >
              <span class="ratio-icon" :style="{ aspectRatio: item.replace(':', ' / ') }"></span>
              <strong>{{ item }}</strong>
            </button>
          </div>
        </div>
      </div>
    </transition>

    <transition name="modal">
      <div v-if="previewWork" class="modal-backdrop" @click.self="previewWork = null">
        <div class="image-preview-modal">
          <button type="button" class="modal-close" @click="previewWork = null">×</button>
          <div class="preview-backdrop-art">
            <img :src="previewWork.imageUrl" alt="" aria-hidden="true" />
          </div>
          <section class="preview-stage">
            <div class="preview-image-frame">
              <img :src="previewWork.imageUrl" :alt="previewWork.title" />
            </div>
            <div v-if="previewWork.tags" class="preview-tags">
              <strong>相关标签</strong>
              <div>
                <span v-for="tag in previewWork.tags.split(',').filter(Boolean)" :key="tag">{{ tag.trim() }}</span>
              </div>
            </div>
          </section>
          <aside class="preview-side">
            <div class="preview-info-card">
              <div>
                <span>分类</span>
                <strong>{{ previewWork.title }}</strong>
              </div>
              <div>
                <span>作者</span>
                <button type="button" class="preview-author-link" @click="showAuthorWorks(previewWork)">@{{ previewWork.ownerName }}</button>
              </div>
              <div>
                <span>下载量</span>
                <strong>{{ previewWork.downloadCount || 0 }}</strong>
              </div>
              <div>
                <span>发布时间</span>
                <strong>{{ formatDate(previewWork.createdAt) || '-' }}</strong>
              </div>
            </div>

            <button type="button" class="preview-author-card" @click="showAuthorWorks(previewWork)">
              <div class="preview-author-avatar">{{ previewWork.ownerName?.slice(0, 1) || 'U' }}</div>
              <div>
                <strong>{{ previewWork.ownerName }}</strong>
                <span>{{ previewWork.likeCount || 0 }} 赞 · {{ previewWork.favoriteCount || 0 }} 收藏 · {{ previewWork.commentCount || 0 }} 评论</span>
              </div>
            </button>

            <div class="preview-actions">
              <button class="download-main" @click="download(previewWork)">下载</button>
              <button :class="{ active: previewWork.liked }" @click="toggleLike(previewWork)">赞 {{ previewWork.likeCount || 0 }}</button>
              <button :class="{ active: previewWork.favorited }" @click="toggleFavorite(previewWork)">收藏</button>
              <button @click="copyPrompt(previewWork)">复制提示词</button>
            </div>

            <div class="preview-prompt-card">
              <strong>提示词</strong>
              <p>{{ previewWork.prompt }}</p>
            </div>

            <div class="comment-panel">
              <div class="comment-head">
                <strong>评论</strong>
                <span>{{ previewComments.length }} 条</span>
              </div>
              <div class="comment-list">
                <p v-if="commentsLoading" class="comment-empty">评论加载中...</p>
                <article v-for="comment in previewComments" :key="comment.id" class="comment-item">
                  <strong>{{ comment.username }}</strong>
                  <span>{{ formatMessageTime(comment.createdAt) }}</span>
                  <p>{{ comment.content }}</p>
                </article>
                <p v-if="!commentsLoading && !previewComments.length" class="comment-empty">暂无评论</p>
              </div>
              <form class="comment-form" @submit.prevent="submitComment">
                <input v-model="commentText" placeholder="写下你的评论" />
                <button type="submit">发送</button>
              </form>
            </div>
          </aside>
        </div>
      </div>
    </transition>

    <transition name="modal">
      <div v-if="showUploadModal" class="modal-backdrop" @click.self="closeUploadArtworkModal">
        <form class="upload-artwork-modal" @submit.prevent="submitUploadArtwork">
          <button type="button" class="modal-close" @click="closeUploadArtworkModal">×</button>
          <div class="modal-head">
            <h2>发布作品</h2>
            <p>上传自己的图片，补充标题、提示词和标签后发布到作品库</p>
          </div>
          <label class="upload-drop">
            <input type="file" accept="image/*" @change="setUploadArtworkImage($event.target.files[0])" />
            <img v-if="uploadArtworkPreview" :src="uploadArtworkPreview" alt="作品预览" />
            <span v-else>点击上传图片</span>
          </label>
          <label>
            <span>标题</span>
            <input v-model="uploadArtworkForm.title" maxlength="120" placeholder="输入作品标题" />
          </label>
          <label>
            <span>提示词 / 描述</span>
            <textarea v-model="uploadArtworkForm.prompt" maxlength="1200" rows="3" placeholder="描述这张作品，公开后其他人可以查看" />
          </label>
          <label>
            <span>标签</span>
            <input v-model="uploadArtworkForm.tags" maxlength="300" placeholder="例如：人像, 海边, 电影感" />
          </label>
          <div class="upload-options">
            <button
              v-for="item in ratios"
              :key="item"
              type="button"
              :class="{ active: uploadArtworkForm.ratio === item }"
              @click="uploadArtworkForm.ratio = item"
            >
              {{ item }}
            </button>
            <label class="publish-check">
              <input v-model="uploadArtworkForm.publicWork" type="checkbox" />
              公开
            </label>
          </div>
          <button class="modal-submit" :disabled="loading">{{ loading ? '发布中...' : '发布作品' }}</button>
        </form>
      </div>
    </transition>

    <transition name="modal">
      <div v-if="editingWork" class="modal-backdrop" @click.self="editingWork = null">
        <form class="metadata-modal" @submit.prevent="saveMetadata">
          <button type="button" class="modal-close" @click="editingWork = null">×</button>
          <div class="modal-head">
            <h2>编辑作品信息</h2>
            <p>为自己的作品补充标题和标签，标签用逗号分隔</p>
          </div>
          <label>
            <span>标题</span>
            <input v-model="metadataForm.title" maxlength="120" placeholder="输入作品标题" />
          </label>
          <label>
            <span>标签</span>
            <input v-model="metadataForm.tags" maxlength="300" placeholder="例如：人像, 海边, 电影感" />
          </label>
          <button class="modal-submit" type="submit">保存</button>
        </form>
      </div>
    </transition>
  </main>
</template>

