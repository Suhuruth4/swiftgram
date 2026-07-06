import { useEffect } from 'react'
import './App.css'
import { AuthScreen } from './components/AuthScreen'
import { ChatApp } from './components/ChatApp'
import { setAuthToken } from './lib/api'
import { loadToken } from './lib/storage'
import { useAppStore } from './store/useAppStore'

export default function App() {
  const token = useAppStore((s) => s.token)
  const setToken = useAppStore((s) => s.setToken)

  useEffect(() => {
    const cached = loadToken()
    if (cached && !token) {
      setToken(cached)
      setAuthToken(cached)
    }
  }, [token, setToken])

  if (!token) {
    return <AuthScreen />
  }

  return <ChatApp />
}
