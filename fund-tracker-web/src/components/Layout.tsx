import { useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Menu } from 'antd'
import {
  DashboardOutlined, FundOutlined, WalletOutlined, SwapOutlined,
  MessageOutlined, MenuFoldOutlined, MenuUnfoldOutlined,
} from '@ant-design/icons'

const menuItems = [
  { key: '/chat', icon: <MessageOutlined />, label: 'AI 对话' },
  { key: '/dashboard', icon: <DashboardOutlined />, label: '总览' },
  { key: '/funds', icon: <FundOutlined />, label: '基金市场' },
  { key: '/portfolio', icon: <WalletOutlined />, label: '我的持仓' },
  { key: '/transactions', icon: <SwapOutlined />, label: '交易记录' },
]

export default function Layout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()

  const currentPath = menuItems.find(m => location.pathname.startsWith(m.key))?.key || '/'

  return (
    <div className="ft-layout">
      <nav className={`ft-sidebar-nav${collapsed ? ' collapsed' : ''}`}>
        <div className="ft-sidebar-logo">
          <div className="ft-sidebar-logo-icon">基</div>
          {!collapsed && <span className="ft-sidebar-logo-text">基金跟踪</span>}
          <button className="ft-sidebar-collapse-btn" onClick={() => setCollapsed(!collapsed)} title={collapsed ? '展开' : '收起'}>
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </button>
        </div>
        <div style={{ flex: 1, overflow: 'auto', padding: '8px 0' }}>
          <Menu
            mode="inline"
            inlineCollapsed={collapsed}
            selectedKeys={[currentPath]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </div>
      </nav>
      <div className={`ft-main-area${collapsed ? ' collapsed' : ''}`}>
        <main className="ft-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
