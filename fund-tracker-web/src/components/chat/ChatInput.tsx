import { useState, useRef } from 'react'
import { SendOutlined, LineChartOutlined, WalletOutlined, FundOutlined, BarChartOutlined } from '@ant-design/icons'
import { useChatStore } from '../../store/chatStore'

const quickActions = [
  { icon: <LineChartOutlined />, label: '今日市场', prompt: '今天A股市场整体表现如何？有哪些重要新闻？行业板块有什么变化？' },
  { icon: <WalletOutlined />, label: '分析持仓', prompt: '帮我分析我的基金持仓，看看盈亏情况和风险' },
  { icon: <FundOutlined />, label: '热门基金', prompt: '最近哪些基金表现比较好？帮我推荐几只' },
  { icon: <BarChartOutlined />, label: '指数估值', prompt: '现在主要指数的估值水平怎么样？' },
]

export default function ChatInput() {
  const [value, setValue] = useState('')
  const streaming = useChatStore((s) => s.streaming)
  const isComposing = useRef(false)

  const handleSend = () => {
    const text = value.trim()
    if (!text || streaming) return
    setValue('')
    useChatStore.getState().sendMessage(text)
  }

  return (
    <div className="ft-input-area">
      <div className="ft-quick-strip">
        {quickActions.map((action) => (
          <button
            key={action.label}
            className="ft-quick-chip"
            disabled={streaming}
            onClick={() => useChatStore.getState().sendMessage(action.prompt)}
          >
            {action.icon}
            {action.label}
          </button>
        ))}
      </div>
      <div className="ft-input-bar">
        <textarea
          className="ft-input-field"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onCompositionStart={() => { isComposing.current = true }}
          onCompositionEnd={() => { isComposing.current = false }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey && !isComposing.current) {
              e.preventDefault()
              handleSend()
            }
          }}
          placeholder="输入你想了解的内容..."
          rows={1}
          disabled={streaming}
        />
        <button
          className="ft-btn-send"
          onClick={handleSend}
          disabled={streaming || !value.trim()}
        >
          <SendOutlined />
        </button>
      </div>
    </div>
  )
}
