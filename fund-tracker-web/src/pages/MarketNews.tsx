import { useQuery } from '@tanstack/react-query'
import { Card, Spin, Alert, Tag, Typography } from 'antd'
import { fetchNews } from '../api/market'
import { formatDate } from '../utils/format'

const { Title, Paragraph, Text } = Typography

export default function MarketNews() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['market-news'],
    queryFn: fetchNews,
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (isError) return <Alert type="error" message="加载市场资讯失败" banner />

  const newsItems = data?.newsItems

  return (
    <div>
      <Card>
        <Title level={4}>{data?.title || '市场简报'}</Title>
        <div style={{ marginBottom: 16 }}>
          <Tag>{data?.date ? formatDate(data.date) : '-'}</Tag>
          <Tag color="blue">{data?.source || '未知来源'}</Tag>
        </div>
        {data?.summary && (
          <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
            {data.summary}
          </Paragraph>
        )}
      </Card>
      {newsItems && newsItems.length > 0 && (
        <Card title="相关资讯" style={{ marginTop: 16 }}>
          {newsItems.map((item, index) => (
            <div
              key={index}
              style={{
                marginBottom: 12,
                paddingBottom: 12,
                borderBottom: index < newsItems.length - 1 ? '1px solid var(--ft-border)' : 'none',
              }}
            >
              <a
                href={item.url}
                target="_blank"
                rel="noopener noreferrer"
                style={{ fontSize: 15, fontWeight: 500, color: 'var(--ft-text-primary)' }}
              >
                {item.title}
              </a>
              <div style={{ marginTop: 4 }}>
                <Text type="secondary">{item.date}</Text>
                {item.summary && (
                  <Paragraph
                    style={{ marginTop: 4, marginBottom: 0 }}
                    type="secondary"
                    ellipsis={{ rows: 2 }}
                  >
                    {item.summary}
                  </Paragraph>
                )}
              </div>
            </div>
          ))}
        </Card>
      )}
    </div>
  )
}
