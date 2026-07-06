export type IdentityKeyPair = { publicKey: string; secretKey: string }

type ChatKeyStore = Record<string, Record<string, string>>

const IDENTITY_KEY = 'cove_identity'
const TOKEN_KEY = 'cove_token'
const USER_KEY = 'cove_user'
const CHAT_KEYS = 'cove_chatkeys'

// One-time migration from the old SwiftGram branding so existing sessions
// (especially E2EE identity keys) survive the rename.
const LEGACY_PREFIX = 'swiftgram_'
for (const key of [IDENTITY_KEY, TOKEN_KEY, USER_KEY, CHAT_KEYS]) {
  const legacyKey = LEGACY_PREFIX + key.slice('cove_'.length)
  const legacyValue = localStorage.getItem(legacyKey)
  if (legacyValue !== null) {
    if (localStorage.getItem(key) === null) {
      localStorage.setItem(key, legacyValue)
    }
    localStorage.removeItem(legacyKey)
  }
}

export const loadIdentity = (): IdentityKeyPair | null => {
  const raw = localStorage.getItem(IDENTITY_KEY)
  return raw ? (JSON.parse(raw) as IdentityKeyPair) : null
}

export const saveIdentity = (identity: IdentityKeyPair) => {
  localStorage.setItem(IDENTITY_KEY, JSON.stringify(identity))
}

export const loadToken = () => localStorage.getItem(TOKEN_KEY)
export const saveToken = (token: string) => localStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

export const loadUser = () => {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) : null
}
export const saveUser = (user: unknown) => localStorage.setItem(USER_KEY, JSON.stringify(user))
export const clearUser = () => localStorage.removeItem(USER_KEY)

export const loadChatKeys = (): ChatKeyStore => {
  const raw = localStorage.getItem(CHAT_KEYS)
  return raw ? (JSON.parse(raw) as ChatKeyStore) : {}
}

export const saveChatKey = (chatId: string, keyId: number, keyBase64: string) => {
  const store = loadChatKeys()
  const chatKeys = store[chatId] ?? {}
  chatKeys[keyId.toString()] = keyBase64
  store[chatId] = chatKeys
  localStorage.setItem(CHAT_KEYS, JSON.stringify(store))
}

export const getChatKey = (chatId: string, keyId: number): string | null => {
  const store = loadChatKeys()
  return store[chatId]?.[keyId.toString()] ?? null
}

export const clearChatKeys = () => localStorage.removeItem(CHAT_KEYS)
