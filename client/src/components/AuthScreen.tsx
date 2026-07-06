import { useState } from 'react'
import axios from 'axios'
import { api, setAuthToken } from '../lib/api'
import { generateIdentityKeyPair } from '../lib/crypto'
import { loadIdentity, saveIdentity, saveToken, saveUser } from '../lib/storage'
import { useAppStore } from '../store/useAppStore'
import { Icon, Logo } from './ui'

const errorMessage = (error: unknown, fallback: string) => {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

export const AuthScreen = () => {
  const setToken = useAppStore((s) => s.setToken)
  const setUser = useAppStore((s) => s.setUser)
  const [channel, setChannel] = useState<'email' | 'phone'>('email')
  const [identifier, setIdentifier] = useState('')
  const [code, setCode] = useState('')
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [step, setStep] = useState<'request' | 'verify'>('request')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const requestOtp = async () => {
    setError(null)
    setLoading(true)
    try {
      await api.post('/auth/request-otp', { channel, identifier })
      setStep('verify')
    } catch (err: unknown) {
      setError(errorMessage(err, 'Failed to send the code. Try again.'))
    } finally {
      setLoading(false)
    }
  }

  const verifyOtp = async () => {
    setError(null)
    setLoading(true)
    try {
      let identity = loadIdentity()
      if (!identity) {
        identity = generateIdentityKeyPair()
        saveIdentity(identity)
      }
      const res = await api.post('/auth/verify-otp', {
        channel,
        identifier,
        code,
        username,
        displayName: displayName || undefined,
        identityKey: identity.publicKey,
      })
      const { token, user } = res.data
      saveToken(token)
      saveUser(user)
      setAuthToken(token)
      setToken(token)
      setUser(user)
    } catch (err: unknown) {
      setError(errorMessage(err, 'That code did not work. Try again.'))
    } finally {
      setLoading(false)
    }
  }

  const submitOnEnter = (action: () => void, enabled: boolean) => (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && enabled) {
      e.preventDefault()
      action()
    }
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-brand">
          <Logo size={42} />
          <div>
            <div className="auth-brand-name">Cove</div>
            <div className="auth-brand-tag">Private messaging</div>
          </div>
        </div>

        {step === 'request' ? (
          <>
            <h1>Welcome</h1>
            <p className="auth-sub">Sign in with a one-time code. No passwords, ever.</p>
            <div className="segmented">
              <button className={channel === 'email' ? 'active' : ''} onClick={() => setChannel('email')}>
                Email
              </button>
              <button className={channel === 'phone' ? 'active' : ''} onClick={() => setChannel('phone')}>
                Phone
              </button>
            </div>
            <div className="auth-form">
              <label>{channel === 'email' ? 'Email address' : 'Phone number'}</label>
              <input
                autoFocus
                type={channel === 'email' ? 'email' : 'tel'}
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                onKeyDown={submitOnEnter(requestOtp, Boolean(identifier) && !loading)}
                placeholder={channel === 'email' ? 'you@example.com' : '+1 555 123 4567'}
              />
              {error && <div className="auth-error">{error}</div>}
              <button className="btn-primary" disabled={!identifier || loading} onClick={requestOtp}>
                {loading ? 'Sending code…' : 'Continue'}
              </button>
            </div>
          </>
        ) : (
          <>
            <h1>Check your {channel === 'email' ? 'inbox' : 'phone'}</h1>
            <p className="auth-sub">
              We sent a 6-digit code to <strong>{identifier}</strong>.
            </p>
            <div className="auth-form">
              <label>Verification code</label>
              <input
                autoFocus
                className="otp-input"
                inputMode="numeric"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                onKeyDown={submitOnEnter(verifyOtp, Boolean(code) && !loading)}
                placeholder="••••••"
              />
              <label>Username</label>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, ''))}
                onKeyDown={submitOnEnter(verifyOtp, Boolean(code && username) && !loading)}
                placeholder="e.g. sam_92"
                maxLength={20}
              />
              <div className="auth-hint">
                3–20 characters; letters, numbers, underscores. Others find you by this. Existing accounts keep
                their current username.
              </div>
              <label>Display name (optional)</label>
              <input
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                onKeyDown={submitOnEnter(verifyOtp, Boolean(code && username) && !loading)}
                placeholder="How others see you"
              />
              {error && <div className="auth-error">{error}</div>}
              <button className="btn-primary" disabled={!code || !username || loading} onClick={verifyOtp}>
                {loading ? 'Verifying…' : 'Sign in'}
              </button>
              <button className="auth-link" onClick={() => setStep('request')}>
                Use a different {channel === 'email' ? 'email' : 'number'}
              </button>
            </div>
          </>
        )}

        <div className="auth-footer">
          <Icon name="lock" size={13} />
          <span>End-to-end encrypted · Keys never leave your device</span>
        </div>
      </div>
    </div>
  )
}
