import { useEffect, useState } from 'react'
import axios from 'axios'
import { api } from '../lib/api'
import type { MemberView } from '../store/useAppStore'
import { Avatar, Icon } from './ui'

type SearchUser = {
  id: string
  username?: string | null
  displayName?: string | null
  email?: string | null
  phone?: string | null
}

const nameOf = (user: SearchUser) => user.displayName ?? user.email ?? user.phone ?? user.id

const searchErrorMessage = (error: unknown) => {
  if (axios.isAxiosError(error) && error.response?.status === 401) {
    return 'Search needs a fresh sign in. Log out and sign back in.'
  }
  return 'Could not search. Check that the backend is running.'
}

export const NewChatModal = ({
  open,
  onClose,
  onCreate,
}: {
  open: boolean
  onClose: () => void
  onCreate: (members: MemberView[], title?: string) => void
}) => {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchUser[]>([])
  const [selected, setSelected] = useState<SearchUser[]>([])
  const [title, setTitle] = useState('')
  const [searchError, setSearchError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      setQuery('')
      setResults([])
      setSelected([])
      setTitle('')
      setSearchError(null)
    }
  }, [open])

  useEffect(() => {
    const trimmedQuery = query.trim()
    if (!trimmedQuery) {
      setResults([])
      setSearchError(null)
      return
    }
    const timer = setTimeout(async () => {
      try {
        setSearchError(null)
        const res = await api.get('/users/search', { params: { q: trimmedQuery } })
        setResults(res.data)
      } catch (error) {
        setResults([])
        setSearchError(searchErrorMessage(error))
      }
    }, 300)
    return () => clearTimeout(timer)
  }, [query])

  const toggleSelect = (user: SearchUser) => {
    if (selected.find((s) => s.id === user.id)) {
      setSelected(selected.filter((s) => s.id !== user.id))
    } else {
      setSelected([...selected, user])
    }
  }

  if (!open) return null

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>New conversation</h3>
          <button className="icon-btn" onClick={onClose} title="Close">
            <Icon name="close" size={16} />
          </button>
        </div>
        <div className="search-box">
          <Icon name="search" size={15} />
          <input
            autoFocus
            placeholder="Search by username, name, email or phone"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        {selected.length > 0 && (
          <div className="selected-chips">
            {selected.map((user) => (
              <span key={user.id} className="selected-chip">
                {nameOf(user)}
                <button onClick={() => toggleSelect(user)} title="Remove">
                  <Icon name="close" size={12} />
                </button>
              </span>
            ))}
          </div>
        )}
        <div className="modal-results">
          {searchError && <div className="modal-hint">{searchError}</div>}
          {!searchError && results.length === 0 && query.trim() && (
            <div className="modal-hint">No one found for "{query.trim()}"</div>
          )}
          {!searchError && results.length === 0 && !query.trim() && (
            <div className="modal-hint">Find people to start an encrypted conversation.</div>
          )}
          {results.map((user) => {
            const isSelected = Boolean(selected.find((s) => s.id === user.id))
            return (
              <button
                key={user.id}
                className={`modal-user ${isSelected ? 'selected' : ''}`}
                onClick={() => toggleSelect(user)}
              >
                <Avatar name={nameOf(user)} size={36} />
                <div className="modal-user-meta">
                  <div className="modal-user-name">{nameOf(user)}</div>
                  <div className="modal-user-sub">
                    {user.username ? `@${user.username}` : user.email ?? user.phone ?? ''}
                  </div>
                </div>
                {isSelected && (
                  <span className="check">
                    <Icon name="check" size={16} />
                  </span>
                )}
              </button>
            )
          })}
        </div>
        {selected.length > 1 && (
          <input
            className="modal-field"
            placeholder="Group name"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        )}
        <div className="modal-actions">
          <button className="btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn-primary"
            disabled={selected.length === 0}
            onClick={() =>
              onCreate(
                selected.map((u) => ({
                  userId: u.id,
                  displayName: nameOf(u),
                  role: 'member',
                })),
                title || undefined
              )
            }
          >
            {selected.length > 1 ? 'Create group' : 'Start chat'}
          </button>
        </div>
      </div>
    </div>
  )
}
