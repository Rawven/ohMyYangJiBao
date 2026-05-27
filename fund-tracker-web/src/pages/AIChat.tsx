import { useState, useEffect, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useChatStore } from '../store/chatStore'
import ChatPanel from '../components/chat/ChatPanel'
import ChatMessages from '../components/chat/ChatMessages'
import ChatInput from '../components/chat/ChatInput'
import { PlusOutlined, MenuOutlined } from '@ant-design/icons'
import '../styles/chat.css'

export default function AIChat() {
  const loading = useChatStore((s) => s.loading)
  const streaming = useChatStore((s) => s.streaming)
  const reset = useChatStore((s) => s.reset)
  const sendMessage = useChatStore((s) => s.sendMessage)
  const messages = useChatStore((s) => s.messages)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [searchParams] = useSearchParams()
  const autoSent = useRef(false)

  // 从其他页面带 query 参数跳过来时，自动发送消息
  useEffect(() => {
    const q = searchParams.get('q')
    if (q && !autoSent.current && messages.length === 0) {
      autoSent.current = true
      // 小延迟等页面渲染完毕
      setTimeout(() => sendMessage(q), 100)
    }
  }, [searchParams, sendMessage, messages.length])

  return (
    <div className="ft-page">
      {historyOpen && <div className="ft-drawer-backdrop" onClick={() => setHistoryOpen(false)} />}
      <ChatPanel visible={historyOpen} onClose={() => setHistoryOpen(false)} />

      <div className="ft-main">
        <div className="ft-chat-topbar">
          <div className="ft-chat-topbar-left">
            <button
              className="ft-chat-topbar-btn"
              onClick={() => setHistoryOpen(true)}
              title="历史会话"
            >
              <MenuOutlined />
            </button>
          </div>
          <div className="ft-chat-topbar-title">AI 对话</div>
          <div className="ft-chat-topbar-right">
            <button
              className="ft-chat-topbar-btn"
              onClick={reset}
              disabled={streaming}
              title="新对话"
            >
              <PlusOutlined />
            </button>
          </div>
        </div>

        {loading ? (
          <div className="ft-loading">
            <div className="ft-loading-spinner" />
            <span className="ft-loading-text">加载对话历史...</span>
          </div>
        ) : (
          <>
            <ChatMessages />
            <ChatInput />
          </>
        )}
      </div>
    </div>
  )
}
