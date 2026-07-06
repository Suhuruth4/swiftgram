import { create } from 'zustand'

export type User = {
  id: string
  username?: string | null
  email?: string | null
  phone?: string | null
  displayName?: string | null
  avatarUrl?: string | null
  identityKey?: string | null
}

export type MemberView = {
  userId: string
  displayName?: string | null
  role: string
  joinedAt?: string | null
  lastReadAt?: string | null
}

export type Message = {
  id: string
  chatId: string
  senderId: string
  ciphertext: string
  nonce: string
  keyId: number
  type: string
  attachmentId?: string | null
  replyToId?: string | null
  createdAt: string
  editedAt?: string | null
  deletedAt?: string | null
  plaintext?: string | null
  reactions?: Reaction[]
}

export type Reaction = {
  messageId: string
  userId: string
  reaction: string
}

export type ChatSummary = {
  id: string
  type: string
  title?: string | null
  createdAt: string
  lastMessageAt?: string | null
  unreadCount: number
  members: MemberView[]
  lastMessage?: Message | null
}

export type AttachmentMeta = {
  id: string
  originalName?: string | null
  mimeType?: string | null
  size?: number | null
  encNonce?: string | null
  encAlg?: string | null
  objectUrl?: string | null
}

export type Pin = {
  chatId: string
  messageId: string
  pinnedBy?: string | null
  pinnedAt?: string | null
}

type AppState = {
  token?: string
  user?: User
  chats: ChatSummary[]
  activeChatId?: string
  messages: Record<string, Message[]>
  attachments: Record<string, AttachmentMeta>
  pins: Record<string, Pin[]>
  typing: Record<string, { userId?: string | null; typing: boolean }>
  setToken: (token?: string) => void
  setUser: (user?: User) => void
  setChats: (chats: ChatSummary[]) => void
  setActiveChat: (id?: string) => void
  setMessages: (chatId: string, messages: Message[]) => void
  addMessage: (chatId: string, message: Message) => void
  updateMessage: (chatId: string, message: Message) => void
  removeMessage: (chatId: string, messageId: string) => void
  setAttachment: (meta: AttachmentMeta) => void
  setPins: (chatId: string, pins: Pin[]) => void
  updateMemberRead: (chatId: string, userId: string, readAt?: string | null) => void
  setTyping: (chatId: string, userId: string | null | undefined, typing: boolean) => void
}

export const useAppStore = create<AppState>((set) => ({
  chats: [],
  messages: {},
  attachments: {},
  pins: {},
  typing: {},
  setToken: (token) => set({ token }),
  setUser: (user) => set({ user }),
  setChats: (chats) => set({ chats }),
  setActiveChat: (id) => set({ activeChatId: id }),
  setMessages: (chatId, messages) =>
    set((state) => ({ messages: { ...state.messages, [chatId]: messages } })),
  addMessage: (chatId, message) =>
    set((state) => {
      const current = state.messages[chatId] ?? []
      if (current.find((m) => m.id === message.id)) return state
      return { messages: { ...state.messages, [chatId]: [...current, message] } }
    }),
  updateMessage: (chatId, message) =>
    set((state) => {
      const current = state.messages[chatId] ?? []
      const updated = current.map((m) => (m.id === message.id ? { ...m, ...message } : m))
      return { messages: { ...state.messages, [chatId]: updated } }
    }),
  removeMessage: (chatId, messageId) =>
    set((state) => {
      const current = state.messages[chatId] ?? []
      return { messages: { ...state.messages, [chatId]: current.filter((m) => m.id !== messageId) } }
    }),
  setAttachment: (meta) =>
    set((state) => ({ attachments: { ...state.attachments, [meta.id]: meta } })),
  setPins: (chatId, pins) => set((state) => ({ pins: { ...state.pins, [chatId]: pins } })),
  updateMemberRead: (chatId, userId, readAt) =>
    set((state) => ({
      chats: state.chats.map((chat) => {
        if (chat.id !== chatId) return chat
        return {
          ...chat,
          members: chat.members.map((m) =>
            m.userId === userId ? { ...m, lastReadAt: readAt ?? m.lastReadAt } : m
          ),
        }
      }),
    })),
  setTyping: (chatId, userId, typing) =>
    set((state) => ({ typing: { ...state.typing, [chatId]: { userId, typing } } })),
}))
