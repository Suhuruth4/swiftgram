import { useMemo, useState } from 'react'
import type { ChatSummary, User } from '../store/useAppStore'
import { formatChatStamp } from '../lib/format'
import { Avatar, Icon, Logo } from './ui'

const previewFor = (chat: ChatSummary) => {
  const last = chat.lastMessage
  if (!last) return 'No messages yet'
  if (last.deletedAt) return 'Message deleted'
  if (last.plaintext) return last.plaintext
  if (last.type === 'image') return '📷 Photo'
  if (last.type === 'audio') return '🎤 Voice note'
  if (last.type === 'file') return '📎 File'
  return '🔒 Encrypted message'
}

export const ChatList = ({
  chats,
  activeChatId,
  onSelect,
  onNewChat,
  onSettings,
  onLogout,
  user,
}: {
  chats: ChatSummary[]
  activeChatId?: string
  onSelect: (chat: ChatSummary) => void
  onNewChat: () => void
  onSettings: () => void
  onLogout: () => void
  user?: User
}) => {
  const [query, setQuery] = useState('')

  const filtered = useMemo(() => {
    if (!query) return chats
    return chats.filter((chat) => {
      const name = chat.title ?? chat.members.map((m) => m.displayName).join(', ')
      return name?.toLowerCase().includes(query.toLowerCase())
    })
  }, [chats, query])

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div className="brand">
          <Logo size={34} />
          <div>
            <div className="brand-name">Cove</div>
            <div className="brand-sub">Private messaging</div>
          </div>
        </div>
        <button className="icon-btn accent" onClick={onNewChat} title="New conversation">
          <Icon name="plus" />
        </button>
      </div>
      <div className="sidebar-search">
        <div className="search-box">
          <Icon name="search" size={15} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search" />
        </div>
      </div>
      <div className="chat-list">
        {filtered.length === 0 && (
          <div className="chat-list-empty">
            {chats.length === 0 ? 'No conversations yet. Start one with +' : 'No matches'}
          </div>
        )}
        {filtered.map((chat) => {
          const title =
            chat.type === 'direct'
              ? chat.members.find((m) => m.userId !== user?.id)?.displayName ?? 'Direct chat'
              : chat.title ?? 'Group'
          const stamp = chat.lastMessage?.createdAt ?? chat.lastMessageAt
          return (
            <button
              key={chat.id}
              className={`chat-item ${activeChatId === chat.id ? 'active' : ''}`}
              onClick={() => onSelect(chat)}
            >
              <Avatar name={title} size={44} />
              <div className="chat-meta">
                <div className="chat-title-row">
                  <span className="chat-title">{title}</span>
                  {stamp && <span className="chat-stamp">{formatChatStamp(stamp)}</span>}
                </div>
                <div className="chat-preview-row">
                  <span className="chat-preview">{previewFor(chat)}</span>
                  {chat.unreadCount > 0 && <span className="badge">{chat.unreadCount}</span>}
                </div>
              </div>
            </button>
          )
        })}
      </div>
      <div className="sidebar-footer">
        <Avatar name={user?.displayName ?? 'You'} size={38} />
        <div className="user-meta">
          <div className="user-name">{user?.displayName ?? 'You'}</div>
          <div className="user-sub">{user?.username ? `@${user.username}` : user?.email ?? user?.phone}</div>
        </div>
        <button className="icon-btn" onClick={onSettings} title="Settings">
          <Icon name="gear" size={17} />
        </button>
        <button className="icon-btn" onClick={onLogout} title="Log out">
          <Icon name="logout" size={17} />
        </button>
      </div>
    </aside>
  )
}
