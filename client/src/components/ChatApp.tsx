import { useEffect, useMemo, useRef, useState } from 'react'
import { Client, type IMessage } from '@stomp/stompjs'
import axios from 'axios'
import { api, setAuthToken } from '../lib/api'
import { createStompClient } from '../lib/ws'
import {
  decryptChatKey,
  decryptMessage,
  encryptBytes,
  encryptChatKeyForUser,
  encryptMessage,
  generateChatKey,
} from '../lib/crypto'
import {
  clearChatKeys,
  clearToken,
  clearUser,
  getChatKey,
  loadIdentity,
  loadToken,
  loadUser,
  saveChatKey,
  saveUser,
} from '../lib/storage'
import { useAppStore } from '../store/useAppStore'
import type { ChatSummary, Message, Reaction, AttachmentMeta, MemberView } from '../store/useAppStore'
import { ChatList } from './ChatList'
import { ChatView } from './ChatView'
import { NewChatModal } from './NewChatModal'
import { SettingsModal } from './SettingsModal'

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

type ChatKeyUser = {
  id: string
  identityKey?: string | null
}

type ChatKeyResponse = {
  encryptedKey: string
  nonce: string
  senderId?: string
}

type ReactionPayload = Reaction & {
  chatId?: string
}

type TypingPayload = {
  userId?: string | null
  typing?: boolean
}

const urlBase64ToUint8Array = (base64String: string) => {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = window.atob(base64)
  const output = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i++) {
    output[i] = raw.charCodeAt(i)
  }
  return output
}

const apiErrorMessage = (error: unknown, fallback: string) => {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

export const ChatApp = () => {
  const token = useAppStore((s) => s.token)
  const user = useAppStore((s) => s.user)
  const chats = useAppStore((s) => s.chats)
  const activeChatId = useAppStore((s) => s.activeChatId)
  const setActiveChat = useAppStore((s) => s.setActiveChat)
  const setChats = useAppStore((s) => s.setChats)
  const setMessages = useAppStore((s) => s.setMessages)
  const addMessage = useAppStore((s) => s.addMessage)
  const updateMessage = useAppStore((s) => s.updateMessage)
  const removeMessage = useAppStore((s) => s.removeMessage)
  const setAttachment = useAppStore((s) => s.setAttachment)
  const setPins = useAppStore((s) => s.setPins)
  const updateMemberRead = useAppStore((s) => s.updateMemberRead)
  const setTyping = useAppStore((s) => s.setTyping)
  const setUser = useAppStore((s) => s.setUser)
  const setToken = useAppStore((s) => s.setToken)

  const [newChatOpen, setNewChatOpen] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const stompRef = useRef<Client | null>(null)
  const subscriptionsRef = useRef<Record<string, () => void>>({})
  const userKeyCache = useRef<Record<string, string>>({})
  const [wsReady, setWsReady] = useState(false)
  const [statusMessage, setStatusMessage] = useState<string | null>(null)

  useEffect(() => {
    const cachedToken = loadToken()
    const cachedUser = loadUser()
    if (cachedToken && !token) {
      setToken(cachedToken)
      api.defaults.headers.common.Authorization = `Bearer ${cachedToken}`
    }
    if (cachedUser && !user) {
      setUser(cachedUser)
    }
  }, [token, user, setToken, setUser])

  useEffect(() => {
    if (!token) return
    const load = async () => {
      try {
        const me = await api.get('/auth/me')
        setUser(me.data)
        saveUser(me.data)
        const chatsRes = await api.get('/chats')
        const hydratedChats = []
        for (const chat of chatsRes.data as ChatSummary[]) {
          hydratedChats.push(await hydrateChatSummary(chat))
        }
        setChats(hydratedChats)
      } catch {
        setStatusMessage('Could not sync chats. Check that the backend and dev services are running.')
      }
    }
    load()
  // hydrateChatSummary reads the latest key cache and store-backed chat keys.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, setChats, setUser])

  useEffect(() => {
    if (!token) return
    const initPush = async () => {
      try {
        if (!('serviceWorker' in navigator)) return
        const reg = await navigator.serviceWorker.ready
        const res = await api.get('/push/vapid-public-key')
        const key = res.data?.publicKey
        if (!key) return
        if ('Notification' in window && Notification.permission === 'default') {
          const permission = await Notification.requestPermission()
          if (permission !== 'granted') return
        }
        const subscription = await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(key),
        })
        await api.post('/push/subscribe', subscription)
      } catch {
        // Push is optional in local development.
      }
    }
    initPush()
  }, [token])

  useEffect(() => {
    if (!token) return
    if (stompRef.current) return
    const client = createStompClient(token, API_BASE)
    client.onConnect = () => {
      setWsReady(true)
    }
    client.onStompError = () => {
      setWsReady(false)
    }
    client.activate()
    stompRef.current = client
    return () => {
      Object.values(subscriptionsRef.current).forEach((unsubscribe) => unsubscribe())
      subscriptionsRef.current = {}
      client.deactivate()
      stompRef.current = null
      setWsReady(false)
    }
  }, [token])

  useEffect(() => {
    if (!wsReady || !stompRef.current) return
    const client = stompRef.current
    const currentSubs = subscriptionsRef.current
    const activeChatIds = new Set(chats.map((chat) => chat.id))
    Object.entries(currentSubs).forEach(([chatId, unsubscribe]) => {
      if (!activeChatIds.has(chatId)) {
        unsubscribe()
        delete currentSubs[chatId]
      }
    })
    chats.forEach((chat) => {
      if (currentSubs[chat.id]) return
      const sub = client.subscribe(`/topic/chats/${chat.id}`, (msg) => onChatEvent(msg))
      currentSubs[chat.id] = () => sub.unsubscribe()
    })
    return () => {
      // no-op cleanup here; handled on disconnect
    }
  // onChatEvent reads current store state to merge realtime updates without resubscribing every render.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wsReady, chats])

  useEffect(() => {
    if (!wsReady || !stompRef.current || !activeChatId) return
    const client = stompRef.current
    const sub = client.subscribe(`/topic/typing/${activeChatId}`, (msg) => {
      try {
        const data = JSON.parse(msg.body) as TypingPayload
        setTyping(activeChatId, data.userId, Boolean(data.typing))
      } catch {
        // Ignore malformed WebSocket events.
      }
    })
    return () => sub.unsubscribe()
  }, [wsReady, activeChatId, setTyping])

  const onChatEvent = async (message: IMessage) => {
    try {
      const event = JSON.parse(message.body) as { type: string; payload: Record<string, unknown> }
      const { type, payload } = event
      if (type === 'message_created') {
        const raw = payload as unknown as Message
        const decrypted = await hydrateMessage(raw)
        addMessage(raw.chatId, decrypted)
        const state = useAppStore.getState()
        const isMine = raw.senderId === state.user?.id
        const isActive = state.activeChatId === raw.chatId
        bumpChatLastMessage(raw.chatId, decrypted, !isMine && !isActive)
        if (!isMine && isActive) {
          api.post(`/chats/${raw.chatId}/read`, { readAt: new Date().toISOString() }).catch(() => {})
        }
      }
      if (type === 'message_updated') {
        const raw = payload as unknown as Message
        const decrypted = await hydrateMessage(raw)
        updateMessage(raw.chatId, decrypted)
      }
      if (type === 'message_deleted') {
        removeMessage(String(payload.chatId), String(payload.id))
      }
      if (type === 'reaction_added' || type === 'reaction_removed') {
        updateReaction(type, payload as unknown as ReactionPayload)
      }
      if (type === 'message_pinned' || type === 'message_unpinned') {
        await refreshPins(String(payload.chatId))
      }
      if (type === 'read') {
        updateMemberRead(String(payload.chatId), String(payload.userId), String(payload.readAt))
      }
    } catch {
      // Ignore malformed WebSocket events.
    }
  }

  const bumpChatLastMessage = (chatId: string, message: Message, incrementUnread = false) => {
    const current = useAppStore.getState().chats
    setChats(
      current.map((chat) =>
        chat.id === chatId
          ? {
              ...chat,
              lastMessage: message,
              lastMessageAt: message.createdAt,
              unreadCount: incrementUnread ? chat.unreadCount + 1 : chat.unreadCount,
            }
          : chat
      )
    )
  }

  const updateReaction = (type: string, payload: ReactionPayload) => {
    const chatId = payload.chatId ?? activeChatId
    if (!chatId) return
    const current = useAppStore.getState().messages[chatId] ?? []
    const updated = current.map((msg) => {
      if (msg.id !== payload.messageId) return msg
      const reactions = msg.reactions ?? []
      if (type === 'reaction_added') {
        const exists = reactions.some((r) => r.userId === payload.userId && r.reaction === payload.reaction)
        if (exists) return msg
        return { ...msg, reactions: [...reactions, payload] }
      }
      return { ...msg, reactions: reactions.filter((r) => !(r.userId === payload.userId && r.reaction === payload.reaction)) }
    })
    setMessages(chatId, updated)
  }

  const refreshPins = async (chatId: string) => {
    try {
      const res = await api.get(`/chats/${chatId}/pins`)
      setPins(chatId, res.data)
    } catch {
      setStatusMessage('Could not refresh pinned messages.')
    }
  }

  const hydrateChatSummary = async (chat: ChatSummary): Promise<ChatSummary> => {
    const normalized: ChatSummary = {
      ...chat,
      lastMessage: chat.lastMessage ? { ...chat.lastMessage, chatId: chat.id } : null,
    }
    if (!normalized.lastMessage) return normalized
    return {
      ...normalized,
      lastMessage: await hydrateMessage(normalized.lastMessage),
    }
  }

  const hydrateMessage = async (raw: Message): Promise<Message> => {
    const key = await ensureChatKey(raw.chatId, raw.keyId, raw.senderId)
    if (!key) return { ...raw, plaintext: null }
    if (raw.type === 'text') {
      const plaintext = decryptMessage(raw.ciphertext, raw.nonce, key) ?? '[Unable to decrypt]'
      return { ...raw, plaintext }
    }
    return { ...raw }
  }

  const ensureChatKey = async (chatId: string, keyId: number, senderId?: string): Promise<string | null> => {
    const cached = getChatKey(chatId, keyId)
    if (cached) return cached
    const identity = loadIdentity()
    if (!identity) return null
    try {
      const res = await api.get(`/chats/${chatId}/keys`, { params: { keyId } })
      const { encryptedKey, nonce, senderId: keySenderId } = res.data as ChatKeyResponse
      const senderKey = await getUserPublicKey(senderId ?? keySenderId)
      if (!senderKey) return null
      const decrypted = decryptChatKey(encryptedKey, nonce, senderKey, identity.secretKey)
      if (decrypted) {
        saveChatKey(chatId, keyId, decrypted)
      }
      return decrypted
    } catch {
      return null
    }
  }

  const getUserPublicKey = async (userId?: string): Promise<string | null> => {
    if (!userId) return null
    if (userKeyCache.current[userId]) return userKeyCache.current[userId]
    try {
      const res = await api.get('/users/keys', { params: { ids: userId } })
      const key = (res.data as ChatKeyUser[])?.[0]?.identityKey
      if (key) {
        userKeyCache.current[userId] = key
        return key
      }
    } catch {
      return null
    }
    return null
  }

  const loadMessages = async (chatId: string) => {
    try {
      const res = await api.get(`/chats/${chatId}/messages`)
      const messages: Message[] = []
      for (const raw of res.data as Message[]) {
        const hydrated = await hydrateMessage(raw)
        hydrated.reactions = raw.reactions ?? []
        messages.push(hydrated)
      }
      setMessages(chatId, messages.reverse())
      setChats(useAppStore.getState().chats.map((c) => (c.id === chatId ? { ...c, unreadCount: 0 } : c)))
      await api.post(`/chats/${chatId}/read`, { readAt: new Date().toISOString() })
    } catch {
      setStatusMessage('Could not load this chat.')
    }
  }

  const openChat = async (chat: ChatSummary) => {
    setActiveChat(chat.id)
    await loadMessages(chat.id)
    await refreshPins(chat.id)
  }

  const createChat = async (members: MemberView[], title?: string) => {
    if (!user) return
    const ids = members.map((m) => m.userId)
    const type = ids.length === 1 ? 'direct' : 'group'
    const res = await api.post('/chats', { type, title, memberIds: ids })
    const chat = await hydrateChatSummary(res.data as ChatSummary)
    const existingLocalChat = chats.find((c) => c.id === chat.id)
    setChats([chat, ...chats.filter((c) => c.id !== chat.id)])
    const existingKey = getChatKey(chat.id, 1) ?? (await ensureChatKey(chat.id, 1))
    if (!existingKey && !existingLocalChat) {
      await createChatKey(chat.id, [user.id, ...ids])
    }
    setNewChatOpen(false)
    await openChat(chat)
  }

  const createChatKey = async (chatId: string, memberIds: string[]) => {
    const identity = loadIdentity()
    if (!identity || !user) return
    const chatKey = generateChatKey()
    const query = memberIds.map((id) => `ids=${id}`).join('&')
    const res = await api.get(`/users/keys?${query}`)
    const entries = (res.data as ChatKeyUser[])
      .filter((u) => u.identityKey)
      .map((u) => {
        if (!u.identityKey) return null
        const boxed = encryptChatKeyForUser(chatKey, u.identityKey, identity.secretKey)
        return { userId: u.id, encryptedKey: boxed.encryptedKey, nonce: boxed.nonce, senderId: user.id }
      })
      .filter((entry): entry is { userId: string; encryptedKey: string; nonce: string; senderId: string } => entry !== null)
    if (entries.length === 0) {
      setStatusMessage('The selected users do not have encryption keys yet.')
      return
    }
    await api.post(`/chats/${chatId}/keys`, { keyId: 1, entries })
    saveChatKey(chatId, 1, chatKey)
  }

  const sendTextMessage = async (chatId: string, text: string, replyToId?: string) => {
    const key = await ensureChatKey(chatId, 1, user?.id)
    if (!key) {
      setStatusMessage('No encryption key is available for this chat.')
      return
    }
    const encrypted = encryptMessage(text, key)
    const res = await api.post(`/chats/${chatId}/messages`, {
      ciphertext: encrypted.ciphertext,
      nonce: encrypted.nonce,
      keyId: 1,
      type: 'text',
      replyToId,
    })
    const hydrated = await hydrateMessage(res.data)
    addMessage(chatId, hydrated)
    bumpChatLastMessage(chatId, hydrated)
  }

  const sendFileMessage = async (chatId: string, file: File, replyToId?: string) => {
    const key = await ensureChatKey(chatId, 1, user?.id)
    if (!key) {
      setStatusMessage('No encryption key is available for this chat.')
      return
    }
    try {
      const buffer = new Uint8Array(await file.arrayBuffer())
      const encrypted = encryptBytes(buffer, key)
      const form = new FormData()
      const cipherBytes = encrypted.ciphertext.slice()
      form.append('file', new Blob([cipherBytes], { type: 'application/octet-stream' }), file.name)
      form.append('originalName', file.name)
      form.append('mimeType', file.type)
      form.append('encNonce', encrypted.nonce)
      form.append('encAlg', 'nacl.secretbox')
      const attachmentRes = await api.post('/attachments', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      const attachment: AttachmentMeta = attachmentRes.data
      setAttachment(attachment)
      const type = file.type.startsWith('image/') ? 'image' : file.type.startsWith('audio/') ? 'audio' : 'file'
      const messagePayload = {
        ciphertext: '',
        nonce: encrypted.nonce,
        keyId: 1,
        type,
        attachmentId: attachment.id,
        ...(replyToId ? { replyToId } : {}),
      }
      const res = await api.post(`/chats/${chatId}/messages`, messagePayload)
      const hydrated = await hydrateMessage(res.data)
      addMessage(chatId, hydrated)
      bumpChatLastMessage(chatId, hydrated)
    } catch (error) {
      setStatusMessage(apiErrorMessage(error, 'Could not send the attachment. Try again.'))
    }
  }

  const sendVoiceNote = async (chatId: string, blob: Blob, replyToId?: string) => {
    const file = new File([blob], `voice-${Date.now()}.webm`, { type: blob.type || 'audio/webm' })
    await sendFileMessage(chatId, file, replyToId)
  }

  const sendTyping = (chatId: string, typing: boolean) => {
    if (!stompRef.current || !stompRef.current.connected) return
    stompRef.current.publish({
      destination: '/app/typing',
      body: JSON.stringify({ chatId, typing }),
    })
  }

  const logout = () => {
    clearChatKeys()
    clearToken()
    clearUser()
    Object.values(subscriptionsRef.current).forEach((unsubscribe) => unsubscribe())
    subscriptionsRef.current = {}
    setAuthToken(undefined)
    setToken(undefined)
    setUser(undefined)
    setChats([])
    setActiveChat(undefined)
  }

  const activeChat = useMemo(() => chats.find((c) => c.id === activeChatId), [activeChatId, chats])

  return (
    <div className="app">
      <ChatList
        chats={chats}
        activeChatId={activeChatId}
        onSelect={openChat}
        onNewChat={() => setNewChatOpen(true)}
        onSettings={() => setSettingsOpen(true)}
        onLogout={logout}
        user={user}
      />
      <ChatView
        chat={activeChat}
        statusMessage={statusMessage}
        onDismissStatus={() => setStatusMessage(null)}
        onSendMessage={sendTextMessage}
        onSendFile={sendFileMessage}
        onSendVoice={sendVoiceNote}
        onTyping={sendTyping}
      />
      <NewChatModal open={newChatOpen} onClose={() => setNewChatOpen(false)} onCreate={createChat} />
      <SettingsModal open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  )
}
