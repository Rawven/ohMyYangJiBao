import { Spin, message } from 'antd'
import { DeleteOutlined, MessageOutlined, CloseOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef } from 'react'
import client from '../../api/client'
import { useChatStore } from '../../store/chatStore'
import type { ApiResponse } from '../../types'

interface Conversation {
  id: number
  title: string
  createdAt: string
}

interface ChatPanelProps {
  visible: boolean
  onClose: () => void
}

export default function ChatPanel({ visible, onClose }: ChatPanelProps) {
  const queryClient = useQueryClient()
  const conversationId = useChatStore((s) => s.conversationId)
  const setConversationId = useChatStore((s) => s.setConversationId)
  const streaming = useChatStore((s) => s.streaming)
  const setLoading = useChatStore((s) => s.setLoading)
  const abortRef = useRef<AbortController | null>(null)

  const { data: conversations, isLoading } = useQuery({
    queryKey: ['conversations'],
    queryFn: async () => {
      const res = await client.get<any, ApiResponse<Conversation[]>>('/ai/conversations')
      return res.data || []
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => client.delete(`/ai/conversations/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
      message.success('对话已删除')
    },
  })

  const loadMessages = useCallback(async (convId: number) => {
    if (abortRef.current) abortRef.current.abort()
    const controller = new AbortController()
    abortRef.current = controller
    setLoading(true)
    useChatStore.setState({ messages: [] })
    try {
      const res = await client.get<any, ApiResponse<any[]>>(`/ai/conversations/${convId}`, {
        signal: controller.signal,
      })
      const msgs = (res.data || []).map((msg: any) => ({
        id: msg.id.toString(),
        role: msg.role,
        content: msg.content || '',
      }))
      useChatStore.setState({ messages: msgs })
    } catch (e: any) {
      if (e.name !== 'AbortError') console.error('加载消息失败', e)
    } finally {
      setLoading(false)
    }
  }, [setLoading])

  const handleSelectConversation = async (id: number) => {
    if (streaming) return
    setConversationId(id)
    onClose()
    await loadMessages(id)
  }

  return (
    <aside className={`ft-drawer${visible ? ' open' : ''}`}>
      <div className="ft-drawer-header">
        <span>历史会话</span>
        <button className="ft-drawer-close" onClick={onClose}>
          <CloseOutlined />
        </button>
      </div>
      <div className="ft-drawer-body ft-scrollbar">
        {isLoading ? (
          <Spin size="small" style={{ display: 'block', margin: '20px auto', color: '#64748B' }} />
        ) : !conversations || conversations.length === 0 ? (
          <div className="ft-drawer-empty">暂无历史会话</div>
        ) : (
          conversations.map((item: Conversation) => (
            <div
              key={item.id}
              className={`ft-drawer-item${conversationId === item.id ? ' active' : ''}`}
              onClick={() => handleSelectConversation(item.id)}
            >
              <MessageOutlined className="ft-drawer-item-icon" />
              <span className="ft-drawer-item-title">{item.title}</span>
              <button
                className="ft-drawer-item-del"
                onClick={(e) => {
                  e.stopPropagation()
                  deleteMutation.mutate(item.id)
                }}
              >
                <DeleteOutlined />
              </button>
            </div>
          ))
        )}
      </div>
    </aside>
  )
}
