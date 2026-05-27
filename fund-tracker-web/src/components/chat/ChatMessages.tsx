import { useEffect, useRef, useState, useCallback } from 'react'
import { useChatStore, type ChatMessage } from '../../store/chatStore'
import TextBlock from './blocks/TextBlock'
import EChartsBlock from './blocks/EChartsBlock'
import TableBlock from './blocks/TableBlock'
import FundCardsBlock from './blocks/FundCardsBlock'
import { LineChartOutlined, CopyOutlined, ReloadOutlined, StopOutlined, CheckOutlined } from '@ant-design/icons'

function AssistantMessage({ msg, isLast }: { msg: ChatMessage; isLast: boolean }) {
  const [copied, setCopied] = useState(false)
  const streaming = useChatStore((s) => s.streaming)
  const sendMessage = useChatStore((s) => s.sendMessage)
  const removeLastAssistant = useChatStore((s) => s.removeLastAssistant)

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(msg.content).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }, [msg.content])

  const handleRegenerate = useCallback(() => {
    if (streaming) return
    removeLastAssistant()
    // 找到最后一条 user 消息
    const msgs = useChatStore.getState().messages
    let lastUserMsg = ''
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === 'user') {
        lastUserMsg = msgs[i].content
        break
      }
    }
    if (lastUserMsg) {
      sendMessage(lastUserMsg)
    }
  }, [streaming, removeLastAssistant, sendMessage])

  return (
    <div className="ft-msg-assistant-wrap">
      <div className="ft-msg-assistant">
        <div className="ft-msg-assistant-header">
          <span className="ft-msg-assistant-dot" />
          <span className="ft-msg-assistant-label">分析助手</span>
          {!streaming && (
            <div className="ft-msg-assistant-actions">
              <button className="ft-msg-action-btn" onClick={handleCopy} title="复制">
                {copied ? <CheckOutlined /> : <CopyOutlined />}
              </button>
              {copied && <span className="ft-copied-toast">已复制</span>}
              {isLast && (
                <button className="ft-msg-action-btn" onClick={handleRegenerate} title="重新生成">
                  <ReloadOutlined />
                </button>
              )}
            </div>
          )}
        </div>
        <div className="ft-msg-assistant-body">
          {msg.content && <TextBlock content={msg.content} />}
          {msg.blocks?.map((block, i) => {
            switch (block.type) {
              case 'echarts':
                return <div className="ft-block" key={i}><EChartsBlock option={block.option ?? null} /></div>
              case 'table':
                return <div className="ft-block" key={i}><TableBlock columns={block.columns ?? []} dataSource={block.dataSource ?? []} /></div>
              case 'fund-cards':
                return <div className="ft-block" key={i}><FundCardsBlock funds={block.funds ?? []} /></div>
              default:
                return null
            }
          })}
        </div>
      </div>
    </div>
  )
}

export default function ChatMessages() {
  const messages = useChatStore((s) => s.messages)
  const streaming = useChatStore((s) => s.streaming)
  const currentToolCalls = useChatStore((s) => s.currentToolCalls)
  const stopStreaming = useChatStore((s) => s.stopStreaming)
  const sendMessage = useChatStore((s) => s.sendMessage)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, streaming])

  if (messages.length === 0) {
    return (
      <div className="ft-empty">
        <div className="ft-empty-emblem">
          <div className="ft-empty-emblem-ring" />
          <div className="ft-empty-emblem-ring" />
          <div className="ft-empty-emblem-ring" />
          <LineChartOutlined className="ft-empty-emblem-icon" />
        </div>
        <div className="ft-empty-title">有什么我可以帮你的？</div>
        <div className="ft-empty-hint">输入问题或选择快捷指令开始分析</div>
        <div className="ft-empty-examples">
          {['今日市场回顾', '分析我的持仓', '行业板块分析', '指数估值'].map((text) => (
            <button
              key={text}
              className="ft-empty-chip"
              onClick={() => sendMessage(text === '今日市场回顾'
                ? '今天A股市场整体表现如何？有哪些重要新闻？行业板块有什么变化？'
                : text === '分析我的持仓'
                ? '帮我分析我的基金持仓，看看每只基金的盈亏和风险，给出建议'
                : text === '行业板块分析'
                ? '现在哪些行业板块表现比较好？资金流向如何？'
                : '现在主要指数的估值水平怎么样？'
              )}
            >
              {text}
            </button>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="ft-messages ft-scrollbar">
      <div className="ft-msg-list">
        {messages.map((msg, idx) =>
          msg.role === 'user' ? (
            <div key={msg.id} className="ft-msg-user-wrap">
              <div className="ft-msg-user">{msg.content}</div>
            </div>
          ) : (
            <AssistantMessage key={msg.id} msg={msg} isLast={idx === messages.length - 1} />
          )
        )}
      </div>

      {/* 折叠的工具调用指示器 + 停止按钮 */}
      {(streaming || currentToolCalls.length > 0) && (
        <div className="ft-streaming-bar">
          <span className="ft-streaming-dot" />
          {currentToolCalls.length > 0 ? (
            <span className="ft-streaming-label">
              正在查询...
              <span className="ft-streaming-tool-count">（{currentToolCalls.length} 个工具）</span>
            </span>
          ) : (
            <span className="ft-streaming-label">AI 分析中</span>
          )}
          <button className="ft-stop-btn" onClick={() => stopStreaming()}>
            <StopOutlined /> 停止
          </button>
        </div>
      )}

      <div ref={bottomRef} />
    </div>
  )
}
