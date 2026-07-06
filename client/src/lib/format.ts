const DAY_MS = 24 * 60 * 60 * 1000

export const hueFromName = (name: string) => {
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) % 360
  }
  return hash
}

export const isSameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()

export const formatTime = (iso: string) =>
  new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

export const formatChatStamp = (iso: string) => {
  const date = new Date(iso)
  const now = new Date()
  if (isSameDay(date, now)) return formatTime(iso)
  if (now.getTime() - date.getTime() < 6 * DAY_MS) {
    return date.toLocaleDateString([], { weekday: 'short' })
  }
  return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

export const formatDayLabel = (iso: string) => {
  const date = new Date(iso)
  const now = new Date()
  if (isSameDay(date, now)) return 'Today'
  if (isSameDay(date, new Date(now.getTime() - DAY_MS))) return 'Yesterday'
  return date.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' })
}
