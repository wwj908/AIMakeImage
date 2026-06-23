import { reactive } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || `${window.location.protocol}//${window.location.hostname}:8080`

export const state = reactive({
  token: localStorage.getItem('mi_token') || '',
  user: JSON.parse(localStorage.getItem('mi_user') || 'null')
})

export function setAuth(auth) {
  state.token = auth.token
  state.user = auth.user
  localStorage.setItem('mi_token', auth.token)
  localStorage.setItem('mi_user', JSON.stringify(auth.user))
}

export function clearAuth() {
  state.token = ''
  state.user = null
  localStorage.removeItem('mi_token')
  localStorage.removeItem('mi_user')
}

function normalizeArtworkUrls(value) {
  if (!value) return value
  if (Array.isArray(value)) {
    return value.map(normalizeArtworkUrls)
  }
  if (value.content && Array.isArray(value.content)) {
    return {
      ...value,
      content: value.content.map(normalizeArtworkUrls)
    }
  }
  if (typeof value === 'object' && value.imageUrl) {
    const backendBase = `${window.location.protocol}//${window.location.hostname}:8080`
    return {
      ...value,
      imageUrl: value.imageUrl.replace('http://localhost:8080', backendBase),
      sourceImageUrl: value.sourceImageUrl
        ? value.sourceImageUrl.replace('http://localhost:8080', backendBase)
        : value.sourceImageUrl
    }
  }
  return value
}

async function request(path, options = {}) {
  const headers = options.headers || {}
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`
  }
  let res
  try {
    res = await fetch(`${API_BASE}${path}`, { ...options, headers })
  } catch (error) {
    throw new Error(`无法连接后端服务：${API_BASE}`)
  }
  const payload = await res.json()
  if (!res.ok || !payload.success) {
    throw new Error(payload.message || '请求失败')
  }
  return normalizeArtworkUrls(payload.data)
}

export const api = {
  register: (data) => request('/api/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (data) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  generate: (data) => request('/api/artworks/generate', { method: 'POST', body: JSON.stringify(data) }),
  edit: (data, image) => {
    const form = new FormData()
    form.append('image', image)
    form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    return request('/api/artworks/edit', { method: 'POST', body: form })
  },
  myWorks: () => request('/api/artworks/me?size=24'),
  publicWorks: () => request('/api/public/artworks?size=24'),
  publish: (id, publicWork) => request(`/api/artworks/${id}/publish`, {
    method: 'POST',
    body: JSON.stringify({ publicWork })
  }),
  updateMetadata: (id, data) => request(`/api/artworks/${id}/metadata`, {
    method: 'PATCH',
    body: JSON.stringify(data)
  }),
  addDownload: (id) => request(`/api/public/artworks/${id}/download`, { method: 'POST' }),
  like: (id) => request(`/api/artworks/${id}/like`, { method: 'POST' }),
  favorite: (id) => request(`/api/artworks/${id}/favorite`, { method: 'POST' }),
  comments: (id) => request(`/api/public/artworks/${id}/comments`),
  addComment: (id, content) => request(`/api/artworks/${id}/comments`, {
    method: 'POST',
    body: JSON.stringify({ content })
  }),
  chatSessions: () => request('/api/chat-sessions'),
  saveChatSession: (data) => request('/api/chat-sessions', {
    method: 'POST',
    body: JSON.stringify(data)
  }),
  renameChatSession: (clientId, title) => request(`/api/chat-sessions/${encodeURIComponent(clientId)}/rename`, {
    method: 'PATCH',
    body: JSON.stringify({ title })
  }),
  pinChatSession: (clientId, pinned) => request(`/api/chat-sessions/${encodeURIComponent(clientId)}/pin`, {
    method: 'PATCH',
    body: JSON.stringify({ pinned })
  }),
  deleteChatSession: (clientId) => request(`/api/chat-sessions/${encodeURIComponent(clientId)}`, { method: 'DELETE' })
}
