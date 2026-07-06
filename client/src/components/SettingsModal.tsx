import { useEffect, useState } from 'react'
import axios from 'axios'
import { api } from '../lib/api'
import { saveUser } from '../lib/storage'
import { useAppStore } from '../store/useAppStore'
import { Avatar, Icon } from './ui'

const errorMessage = (error: unknown) => {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? 'Could not save your profile.'
  }
  return 'Could not save your profile.'
}

export const SettingsModal = ({ open, onClose }: { open: boolean; onClose: () => void }) => {
  const user = useAppStore((s) => s.user)
  const setUser = useAppStore((s) => s.setUser)
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (open) {
      setUsername(user?.username ?? '')
      setDisplayName(user?.displayName ?? '')
      setError(null)
      setSaved(false)
    }
  }, [open, user])

  if (!open) return null

  const save = async () => {
    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      const res = await api.put('/users/me', { username, displayName })
      setUser(res.data)
      saveUser(res.data)
      setSaved(true)
    } catch (err: unknown) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Settings</h3>
          <button className="icon-btn" onClick={onClose} title="Close">
            <Icon name="close" size={16} />
          </button>
        </div>

        <div className="settings-profile">
          <Avatar name={displayName || username || 'You'} size={52} />
          <div>
            <div className="user-name">{displayName || username || 'You'}</div>
            <div className="user-sub">{username ? `@${username}` : ''}</div>
          </div>
        </div>

        <label className="modal-label">Username</label>
        <input
          className="modal-field"
          value={username}
          maxLength={20}
          onChange={(e) => setUsername(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, ''))}
          placeholder="your_username"
        />

        <label className="modal-label">Display name</label>
        <input
          className="modal-field"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          placeholder="How others see you"
        />

        <label className="modal-label">Signed in as</label>
        <div className="modal-readonly">{user?.email ?? user?.phone ?? 'Unknown'}</div>

        {error && <div className="modal-error">{error}</div>}
        {saved && <div className="modal-success">Profile saved.</div>}

        <div className="modal-actions">
          <button className="btn-ghost" onClick={onClose}>
            Close
          </button>
          <button className="btn-primary" disabled={saving || !username} onClick={save}>
            {saving ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </div>
    </div>
  )
}
