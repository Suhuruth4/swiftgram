import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../lib/api'
import { decryptBytes } from '../lib/crypto'
import { getChatKey } from '../lib/storage'
import { formatDayLabel, formatTime, hueFromName, isSameDay } from '../lib/format'
import { useAppStore } from '../store/useAppStore'
import type { AttachmentMeta, ChatSummary, Message, Pin } from '../store/useAppStore'
import { Composer } from './Composer'
import { Avatar, Icon, Logo } from './ui'

const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🔥']

// Reactions stored before the redesign used shorthand codes.
const LEGACY_REACTIONS: Record<string, string> = {
  '+1': '👍',
  '<3': '❤️',
  lol: '😂',
  wow: '😮',
  sad: '😢',
  fire: '🔥',
}

const displayReaction = (reaction: string) => LEGACY_REACTIONS[reaction] ?? reaction

const GROUP_WINDOW_MS = 5 * 60 * 1000

export const ChatView = ({
  chat,
  statusMessage,
  onDismissStatus,
  onSendMessage,
  onSendFile,
  onSendVoice,
  onTyping,
}: {
  chat?: ChatSummary
  statusMessage?: string | null
  onDismissStatus: () => void
  onSendMessage: (chatId: string, text: string, replyToId?: string) => void
  onSendFile: (chatId: string, file: File, replyToId?: string) => void
  onSendVoice: (chatId: string, blob: Blob, replyToId?: string) => void
  onTyping: (chatId: string, typing: boolean) => void
}) => {
  const user = useAppStore((s) => s.user)
  const messages = useAppStore((s) => (chat ? s.messages[chat.id] ?? [] : []))
  const attachments = useAppStore((s) => s.attachments)
  const setAttachment = useAppStore((s) => s.setAttachment)
  const typing = useAppStore((s) => (chat ? s.typing[chat.id] : undefined))
  const pins = useAppStore((s) => (chat ? s.pins[chat.id] ?? [] : []))
  const updateMessage = useAppStore((s) => s.updateMessage)

  const [replyTo, setReplyTo] = useState<Message | null>(null)
  const [editing, setEditing] = useState<Message | null>(null)
  const [viewer, setViewer] = useState<{ url: string; name?: string | null } | null>(null)
  const listRef = useRef<HTMLDivElement | null>(null)
  const decryptingRef = useRef<Set<string>>(new Set())

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [messages.length])

  useEffect(() => {
    setReplyTo(null)
    setEditing(null)
  }, [chat?.id])

  const memberName = (userId?: string | null) =>
    chat?.members.find((m) => m.userId === userId)?.displayName ?? 'Unknown'

  const title = useMemo(() => {
    if (!chat) return 'Cove'
    if (chat.type === 'direct') {
      return chat.members.find((m) => m.userId !== user?.id)?.displayName ?? 'Direct chat'
    }
    return chat.title ?? 'Group chat'
  }, [chat, user])

  const sendText = async (text: string) => {
    if (!chat) return
    await onSendMessage(chat.id, text, replyTo?.id)
    setReplyTo(null)
  }

  const sendFile = async (file: File) => {
    if (!chat) return
    await onSendFile(chat.id, file, replyTo?.id)
    setReplyTo(null)
  }

  const sendVoice = async (blob: Blob) => {
    if (!chat) return
    await onSendVoice(chat.id, blob, replyTo?.id)
    setReplyTo(null)
  }

  const handleReaction = async (message: Message, reaction: string) => {
    try {
      await api.post(`/messages/${message.id}/reactions`, { reaction })
    } catch {
      // ignore
    }
  }

  const handleDelete = async (message: Message) => {
    try {
      await api.delete(`/messages/${message.id}`)
    } catch {
      // ignore
    }
  }

  const handleEdit = async (message: Message) => {
    if (!message.plaintext) return
    setEditing(message)
  }

  const handleSaveEdit = async (text: string) => {
    if (!editing) return
    try {
      const key = getChatKey(editing.chatId, editing.keyId)
      if (!key) return
      const { encryptMessage } = await import('../lib/crypto')
      const encrypted = encryptMessage(text, key)
      const res = await api.put(`/messages/${editing.id}`, {
        ciphertext: encrypted.ciphertext,
        nonce: encrypted.nonce,
      })
      updateMessage(editing.chatId, { ...editing, ...res.data, plaintext: text })
      setEditing(null)
    } catch {
      // ignore
    }
  }

  const handlePin = async (message: Message) => {
    if (!chat) return
    try {
      await api.post(`/messages/${message.id}/pin`, null, { params: { chatId: chat.id } })
    } catch {
      // ignore
    }
  }

  const handleUnpin = async (message: Message) => {
    if (!chat) return
    try {
      await api.delete(`/messages/${message.id}/pin`, { params: { chatId: chat.id } })
    } catch {
      // ignore
    }
  }

  const decryptAttachment = async (message: Message) => {
    if (!chat || !message.attachmentId) return
    const chatKey = getChatKey(chat.id, message.keyId)
    if (!chatKey) return
    let meta: AttachmentMeta | undefined = attachments[message.attachmentId]
    if (!meta) {
      const metaRes = await api.get(`/attachments/${message.attachmentId}/meta`)
      meta = metaRes.data
      if (meta) {
        setAttachment(meta)
      }
    }
    if (!meta?.encNonce) return
    if (meta.objectUrl) return
    const fileRes = await api.get(`/attachments/${message.attachmentId}`, { responseType: 'arraybuffer' })
    const decrypted = decryptBytes(new Uint8Array(fileRes.data), meta.encNonce, chatKey)
    if (!decrypted) return
    const blob = new Blob([decrypted.slice()], { type: meta.mimeType ?? 'application/octet-stream' })
    const objectUrl = URL.createObjectURL(blob)
    setAttachment({ ...meta, objectUrl })
  }

  // Photos and voice notes decrypt automatically as they come into view of the
  // conversation; generic files stay click-to-download.
  useEffect(() => {
    messages.forEach((msg) => {
      if (msg.deletedAt || !msg.attachmentId) return
      if (msg.type !== 'image' && msg.type !== 'audio') return
      const id = msg.attachmentId
      if (attachments[id]?.objectUrl || decryptingRef.current.has(id)) return
      decryptingRef.current.add(id)
      decryptAttachment(msg).finally(() => decryptingRef.current.delete(id))
    })
  // decryptAttachment is recreated per render but only reads current state.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [messages, attachments])

  const isReadByAll = (message: Message) => {
    if (!chat || !user) return false
    if (message.senderId !== user.id) return false
    const others = chat.members.filter((m) => m.userId !== user.id)
    if (others.length === 0) return false
    return others.every((m) => m.lastReadAt && new Date(m.lastReadAt) >= new Date(message.createdAt))
  }

  if (!chat) {
    return (
      <main className="chat-panel">
        <div className="empty-state">
          <Logo size={56} />
          <h2>Welcome to Cove</h2>
          <p>Pick a conversation from the left, or start a new one.</p>
        </div>
      </main>
    )
  }

  const isGroup = chat.type !== 'direct'
  const pinned = pins[0] ? messages.find((m) => m.id === pins[0].messageId) : undefined

  const renderAttachment = (msg: Message) => {
    const meta = msg.attachmentId ? attachments[msg.attachmentId] : undefined
    if (meta?.objectUrl) {
      if (msg.type === 'image') {
        return (
          <img
            src={meta.objectUrl}
            alt={meta.originalName ?? 'Photo'}
            onClick={() => setViewer({ url: meta.objectUrl!, name: meta.originalName })}
          />
        )
      }
      if (msg.type === 'audio') {
        return <audio controls src={meta.objectUrl} />
      }
      return (
        <a className="file-chip" href={meta.objectUrl} download={meta.originalName ?? 'file'}>
          <Icon name="download" size={15} />
          {meta.originalName ?? 'Download file'}
        </a>
      )
    }
    if (msg.type === 'image' || msg.type === 'audio') {
      return <span className="attachment-pending">Decrypting…</span>
    }
    return (
      <button className="file-chip" onClick={() => decryptAttachment(msg)}>
        <Icon name="file" size={15} />
        {meta?.originalName ?? 'Decrypt file'}
      </button>
    )
  }

  return (
    <main className="chat-panel">
      <div className="chat-header">
        <Avatar name={title} size={40} />
        <div className="chat-header-meta">
          <div className="chat-header-title">{title}</div>
          <div className="chat-header-sub">
            {isGroup ? `${chat.members.length} members` : 'Direct message'}
          </div>
        </div>
        <span className="e2ee-chip">
          <Icon name="lock" size={12} />
          Encrypted
        </span>
      </div>

      {pins.length > 0 && (
        <div className="pinned-bar">
          <Icon name="pin" size={14} />
          <span className="pinned-text">{pinned?.plaintext ?? 'Pinned message'}</span>
        </div>
      )}

      {statusMessage && (
        <div className="status-banner">
          <span>{statusMessage}</span>
          <button onClick={onDismissStatus}>Dismiss</button>
        </div>
      )}

      <div className="message-list" ref={listRef}>
        {messages.map((msg, index) => {
          const prev = messages[index - 1]
          const next = messages[index + 1]
          const mine = msg.senderId === user?.id
          const newDay = !prev || !isSameDay(new Date(prev.createdAt), new Date(msg.createdAt))
          const groupedWithPrev =
            !newDay &&
            prev?.senderId === msg.senderId &&
            new Date(msg.createdAt).getTime() - new Date(prev.createdAt).getTime() < GROUP_WINDOW_MS
          const groupedWithNext =
            next &&
            next.senderId === msg.senderId &&
            isSameDay(new Date(next.createdAt), new Date(msg.createdAt)) &&
            new Date(next.createdAt).getTime() - new Date(msg.createdAt).getTime() < GROUP_WINDOW_MS
          const senderName = memberName(msg.senderId)
          const showName = isGroup && !mine && !groupedWithPrev
          const reactions = msg.reactions ?? []
          const grouped = new Map<string, { count: number; mine: boolean }>()
          for (const r of reactions) {
            const emoji = displayReaction(r.reaction)
            const entry = grouped.get(emoji) ?? { count: 0, mine: false }
            entry.count += 1
            if (r.userId === user?.id) entry.mine = true
            grouped.set(emoji, entry)
          }
          const isPinned = pins.some((p: Pin) => p.messageId === msg.id)

          return (
            <div key={msg.id}>
              {newDay && <div className="day-divider">{formatDayLabel(msg.createdAt)}</div>}
              <div className={`message-row ${mine ? 'mine' : ''} ${groupedWithPrev && !newDay ? 'compact' : ''}`}>
                {isGroup && !mine && (
                  <div className="msg-gutter">
                    {!groupedWithNext && <Avatar name={senderName} size={32} />}
                  </div>
                )}
                <div className="bubble-wrap">
                  {showName && (
                    <div
                      className="sender-name"
                      style={{ color: `hsl(${hueFromName(senderName.toLowerCase())} 70% 68%)` }}
                    >
                      {senderName}
                    </div>
                  )}
                  <div className="bubble">
                    {msg.replyToId && !msg.deletedAt && (() => {
                      const original = messages.find((m) => m.id === msg.replyToId)
                      return (
                        <span className="reply-quote">
                          <span className="reply-quote-name">
                            {original ? memberName(original.senderId) : 'Reply'}
                          </span>
                          <span className="reply-quote-text">
                            {original
                              ? original.plaintext ??
                                (original.type === 'image'
                                  ? '📷 Photo'
                                  : original.type === 'audio'
                                    ? '🎤 Voice note'
                                    : '📎 Attachment')
                              : 'Original message unavailable'}
                          </span>
                        </span>
                      )
                    })()}
                    {msg.deletedAt ? (
                      <span className="bubble-text deleted">Message deleted</span>
                    ) : msg.type === 'text' ? (
                      <span className="bubble-text">{msg.plaintext ?? '🔒 Unable to decrypt'}</span>
                    ) : (
                      renderAttachment(msg)
                    )}
                    <span className="bubble-foot">
                      {msg.editedAt && !msg.deletedAt && <span>edited</span>}
                      <span>{formatTime(msg.createdAt)}</span>
                      {mine && !msg.deletedAt && (
                        <span className={isReadByAll(msg) ? 'read' : ''}>
                          <Icon name={isReadByAll(msg) ? 'checks' : 'check'} size={13} />
                        </span>
                      )}
                    </span>
                  </div>
                  {grouped.size > 0 && (
                    <div className="reaction-row">
                      {[...grouped.entries()].map(([emoji, info]) => (
                        <button
                          key={emoji}
                          className={`reaction-chip ${info.mine ? 'mine' : ''}`}
                          onClick={() => handleReaction(msg, emoji)}
                        >
                          {emoji} {info.count > 1 ? info.count : ''}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                {!msg.deletedAt && (
                  <div className="msg-actions">
                    {QUICK_REACTIONS.map((emoji) => (
                      <button key={emoji} onClick={() => handleReaction(msg, emoji)} title="React">
                        {emoji}
                      </button>
                    ))}
                    <span className="divider" />
                    <button onClick={() => setReplyTo(msg)} title="Reply">
                      <Icon name="reply" size={15} />
                    </button>
                    <button
                      onClick={() => (isPinned ? handleUnpin(msg) : handlePin(msg))}
                      title={isPinned ? 'Unpin' : 'Pin'}
                    >
                      <Icon name="pin" size={15} />
                    </button>
                    {mine && (
                      <>
                        <button onClick={() => handleEdit(msg)} title="Edit">
                          <Icon name="edit" size={15} />
                        </button>
                        <button onClick={() => handleDelete(msg)} title="Delete">
                          <Icon name="trash" size={15} />
                        </button>
                      </>
                    )}
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>

      {typing?.typing && typing.userId !== user?.id && (
        <div className="typing-indicator">
          <span className="typing-dots">
            <span />
            <span />
            <span />
          </span>
          {memberName(typing.userId)} is typing
        </div>
      )}

      {viewer && (
        <div className="lightbox" onClick={() => setViewer(null)}>
          <img src={viewer.url} alt={viewer.name ?? 'Photo'} onClick={(e) => e.stopPropagation()} />
          <div className="lightbox-bar" onClick={(e) => e.stopPropagation()}>
            <span className="lightbox-name">{viewer.name}</span>
            <a className="file-chip" href={viewer.url} download={viewer.name ?? 'photo'}>
              <Icon name="download" size={15} />
              Original
            </a>
            <button className="icon-btn" onClick={() => setViewer(null)} title="Close">
              <Icon name="close" size={16} />
            </button>
          </div>
        </div>
      )}

      <Composer
        onSend={sendText}
        onSendFile={sendFile}
        onSendVoice={sendVoice}
        onTyping={(isTyping) => chat && onTyping(chat.id, isTyping)}
        editing={editing}
        onCancelEdit={() => setEditing(null)}
        onSaveEdit={handleSaveEdit}
        replyTo={replyTo}
        onCancelReply={() => setReplyTo(null)}
        replyToName={replyTo ? memberName(replyTo.senderId) : undefined}
      />
    </main>
  )
}
