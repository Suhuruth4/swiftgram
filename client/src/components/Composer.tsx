import { useEffect, useRef, useState } from 'react'
import type { Message } from '../store/useAppStore'
import { Icon } from './ui'

export const Composer = ({
  onSend,
  onSendFile,
  onSendVoice,
  onTyping,
  editing,
  onCancelEdit,
  onSaveEdit,
  replyTo,
  onCancelReply,
  replyToName,
}: {
  onSend: (text: string) => void
  onSendFile: (file: File) => void
  onSendVoice: (blob: Blob) => void
  onTyping: (typing: boolean) => void
  editing?: Message | null
  onCancelEdit: () => void
  onSaveEdit: (text: string) => void
  replyTo?: Message | null
  onCancelReply: () => void
  replyToName?: string
}) => {
  const [text, setText] = useState('')
  const [recording, setRecording] = useState(false)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const typingTimer = useRef<number | null>(null)

  useEffect(() => {
    if (editing) {
      setText(editing.plaintext ?? '')
      inputRef.current?.focus()
    }
  }, [editing])

  useEffect(() => {
    if (replyTo) {
      inputRef.current?.focus()
    }
  }, [replyTo])

  const handleSend = () => {
    if (!text.trim()) return
    if (editing) {
      onSaveEdit(text.trim())
      setText('')
      onTyping(false)
      return
    }
    onSend(text.trim())
    setText('')
    onTyping(false)
  }

  const MAX_FILES_PER_SEND = 25

  const handleFilePick = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []).slice(0, MAX_FILES_PER_SEND)
    if (fileInputRef.current) fileInputRef.current.value = ''
    for (const file of files) {
      await Promise.resolve(onSendFile(file))
    }
  }

  const toggleRecording = async () => {
    if (recording) {
      recorderRef.current?.stop()
      setRecording(false)
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const recorder = new MediaRecorder(stream)
      recorderRef.current = recorder
      chunksRef.current = []
      recorder.ondataavailable = (e) => chunksRef.current.push(e.data)
      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: 'audio/webm' })
        onSendVoice(blob)
        stream.getTracks().forEach((t) => t.stop())
      }
      recorder.start()
      setRecording(true)
    } catch {
      // Microphone permission denied or unavailable.
    }
  }

  return (
    <div className="composer">
      {(replyTo || editing) && (
        <div className="composer-banner">
          <Icon name={editing ? 'edit' : 'reply'} size={15} />
          <div className="composer-banner-meta">
            <div className="composer-banner-title">
              {editing ? 'Editing message' : `Replying to ${replyToName ?? 'message'}`}
            </div>
            <div className="composer-banner-text">
              {(editing ? editing.plaintext : replyTo?.plaintext) ?? 'Attachment'}
            </div>
          </div>
          <button className="icon-btn" onClick={editing ? onCancelEdit : onCancelReply} title="Cancel">
            <Icon name="close" size={15} />
          </button>
        </div>
      )}
      <div className="composer-row">
        <button className="icon-btn" onClick={() => fileInputRef.current?.click()} title="Attach a file">
          <Icon name="paperclip" size={19} />
        </button>
        <input
          ref={inputRef}
          className="composer-input"
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            onTyping(true)
            if (typingTimer.current) window.clearTimeout(typingTimer.current)
            typingTimer.current = window.setTimeout(() => onTyping(false), 1600)
          }}
          placeholder="Message"
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              handleSend()
            }
          }}
        />
        <button
          className={`icon-btn ${recording ? 'recording' : ''}`}
          onClick={toggleRecording}
          title={recording ? 'Stop and send' : 'Record a voice note'}
        >
          <Icon name={recording ? 'stop' : 'mic'} size={19} />
        </button>
        <button
          className="send-btn"
          onClick={handleSend}
          disabled={!text.trim()}
          title={editing ? 'Save' : 'Send'}
        >
          <Icon name={editing ? 'check' : 'send'} size={18} />
        </button>
      </div>
      <input ref={fileInputRef} type="file" hidden multiple onChange={handleFilePick} />
    </div>
  )
}
