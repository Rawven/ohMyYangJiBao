import { useNavigate } from 'react-router-dom'
import { formatMoney } from '../../../utils/format'

export default function FundCardsBlock({ funds }: { funds: any[] }) {
  const navigate = useNavigate()
  if (!funds || funds.length === 0) return null

  return (
    <div className="ft-fund-grid">
      {funds.map((f: any) => (
        <div
          key={f.code}
          className="ft-fund-card"
          onClick={() => navigate(`/funds/${f.code}`)}
        >
          <div className="ft-fund-card-name">{f.name || f.code}</div>
          <div className="ft-fund-card-row">
            <span className="ft-fund-card-label">净值</span>
            <span className="ft-fund-card-value">{f.nav ? formatMoney(f.nav) : '-'}</span>
          </div>
          <div className="ft-fund-card-row" style={{ marginTop: 6 }}>
            <span className="ft-fund-card-label">日涨跌</span>
            {f.dayIncrease !== undefined ? (
              <span className={f.dayIncrease >= 0 ? 'ft-tag-up' : 'ft-tag-down'}>
                {(f.dayIncrease >= 0 ? '+' : '')}{(f.dayIncrease * 100).toFixed(2)}%
              </span>
            ) : (
              <span className="ft-fund-card-value">--</span>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}
