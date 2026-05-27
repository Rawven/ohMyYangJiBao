import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

function normalize(text: string): string {
  return text
    .replace(/\[ECHARTS:\s*\{.+?\}\]/gs, '')
    .replace(/\[TABLE:\s*\{.+?\}\]/gs, '')
    .replace(/\[FUNDS:\s*\{.+?\}\]/gs, '')
    .replace(/\n{3,}/g, '\n\n')  // 压缩多余空行
    .trim()
}

export default function TextBlock({ content }: { content: string }) {
  const cleanText = normalize(content)
  if (!cleanText) return null

  return (
    <div className="ft-md">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{cleanText}</ReactMarkdown>
    </div>
  )
}
