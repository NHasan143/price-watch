import { useEffect, useMemo, useState } from 'react'
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api, errorMessage } from '../api/client'
import type { PricePoint } from '../api/types'
import { formatDateTime, formatMoney } from '../utils/format'

interface Props {
  productId: number
  productName: string
  targetPrice: number
  currency: string | null
  /** Changes whenever a new price was recorded, which triggers a reload of the history. */
  version: string | null
}

interface ChartPoint {
  time: number
  price: number
  soldOut: boolean
}

const DAY_MS = 24 * 60 * 60 * 1000

function PriceTooltip({
  active,
  payload,
  currency,
  targetPrice,
}: {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: ChartPoint }>
  currency: string | null
  targetPrice: number
}) {
  const point = payload?.[0]?.payload
  if (!active || !point) {
    return null
  }
  return (
    <div className={`chart-tooltip${point.price <= targetPrice ? ' is-under' : ''}`}>
      <div className="chart-tooltip-price">{formatMoney(point.price, currency)}</div>
      <div className="chart-tooltip-time">{formatDateTime(new Date(point.time).toISOString())}</div>
      {point.soldOut && <div className="chart-tooltip-time">Sold out</div>}
    </div>
  )
}

function PriceDot({
  cx,
  cy,
  payload,
  targetPrice,
}: {
  cx?: number
  cy?: number
  payload?: ChartPoint
  targetPrice: number
}) {
  if (cx === undefined || cy === undefined || !payload) {
    return null
  }
  const under = payload.price <= targetPrice
  return (
    <circle
      cx={cx}
      cy={cy}
      r={under ? 5 : 3.5}
      fill={under ? 'var(--sticker)' : 'var(--label)'}
      stroke="var(--ink)"
      strokeWidth={under ? 2 : 1.75}
    />
  )
}

export function PriceChart({ productId, productName, targetPrice, currency, version }: Props) {
  const [points, setPoints] = useState<PricePoint[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [view, setView] = useState<'chart' | 'table'>('chart')

  useEffect(() => {
    let cancelled = false
    api
      .history(productId)
      .then((history) => {
        if (!cancelled) {
          setPoints(history)
          setError(null)
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(errorMessage(e))
      })
    return () => {
      cancelled = true
    }
  }, [productId, version])

  const data: ChartPoint[] = useMemo(
    () => (points ?? []).map((p) => ({ time: Date.parse(p.checkedAt), price: p.price, soldOut: p.availability === 'OUT_OF_STOCK' })),
    [points],
  )

  const head = (
    <div className="history-head">
      <div>
        <h4>Price history</h4>
        <p className="history-sub">{productName}</p>
      </div>
      {data.length > 0 && (
        <div className="segmented" role="group" aria-label="History view">
          <button type="button" aria-pressed={view === 'chart'} onClick={() => setView('chart')}>
            Chart
          </button>
          <button type="button" aria-pressed={view === 'table'} onClick={() => setView('table')}>
            Table
          </button>
        </div>
      )}
    </div>
  )

  if (error || points === null || data.length === 0) {
    return (
      <section className="history" aria-label="Price history">
        {head}
        {error ? (
          <p className="notice notice-void" role="alert">
            {error}
          </p>
        ) : (
          <p className="history-empty">{points === null ? 'Loading price history…' : 'No price history yet.'}</p>
        )}
      </section>
    )
  }

  const prices = [...data.map((d) => d.price), targetPrice]
  const low = Math.min(...prices)
  const high = Math.max(...prices)
  const padding = (high - low) * 0.14 || high * 0.05
  const yDomain: [number, number] = [Math.max(0, low - padding), high + padding]

  const spanMs = data[data.length - 1].time - data[0].time
  // One tick per calendar day (thinned to at most 8) so no date is ever printed twice.
  const dayTicks: number[] = []
  if (spanMs >= 2 * DAY_MS) {
    const day = new Date(data[0].time)
    day.setHours(0, 0, 0, 0)
    for (day.setDate(day.getDate() + 1); day.getTime() <= data[data.length - 1].time; day.setDate(day.getDate() + 1)) {
      dayTicks.push(day.getTime())
    }
  }
  const tickStep = Math.ceil(dayTicks.length / 8)
  const xTicks = dayTicks.length > 0 ? dayTicks.filter((_, i) => i % tickStep === 0) : undefined

  const formatTick = (time: number) =>
    new Date(time).toLocaleString(
      undefined,
      spanMs < 2 * DAY_MS ? { hour: '2-digit', minute: '2-digit' } : { month: 'short', day: 'numeric' },
    )

  const first = data[0].price
  const last = data[data.length - 1].price
  const underCount = data.filter((d) => d.price <= targetPrice).length
  const summary =
    `Price history, ${data.length} ${data.length === 1 ? 'check' : 'checks'}, ` +
    `from ${formatMoney(first, currency)} to ${formatMoney(last, currency)}. ` +
    `Target ${formatMoney(targetPrice, currency)}; ${underCount} ${underCount === 1 ? 'check was' : 'checks were'} at or under it.`

  return (
    <section className="history" aria-label="Price history">
      {head}

      <dl className="history-facts">
        <div>
          <dt>First seen</dt>
          <dd>{formatMoney(first, currency)}</dd>
        </div>
        <div>
          <dt>Now</dt>
          <dd>{formatMoney(last, currency)}</dd>
        </div>
        <div>
          <dt>Checks</dt>
          <dd>{data.length}</dd>
        </div>
        <div>
          <dt>At or under your price</dt>
          <dd>{underCount}</dd>
        </div>
      </dl>

      {view === 'chart' ? (
        <div className="history-chart" role="img" aria-label={summary}>
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={data} margin={{ top: 16, right: 20, bottom: 4, left: 4 }}>
              <ReferenceArea y1={yDomain[0]} y2={targetPrice} fill="var(--sticker)" fillOpacity={0.28} ifOverflow="hidden" />
              <CartesianGrid vertical={false} stroke="var(--hair)" />
              <XAxis
                dataKey="time"
                type="number"
                scale="time"
                domain={['dataMin', 'dataMax']}
                ticks={xTicks}
                tickFormatter={formatTick}
                tickLine={false}
                axisLine={{ stroke: 'var(--ink)' }}
                tick={{ fill: 'var(--ink-3)', fontSize: 12 }}
                minTickGap={48}
              />
              <YAxis
                domain={yDomain}
                tickFormatter={(value: number) => formatMoney(Math.round(value), currency).replace(/[.,]00$/, '')}
                tickLine={false}
                axisLine={false}
                tick={{ fill: 'var(--ink-3)', fontSize: 12 }}
                width={78}
              />
              <Tooltip
                content={<PriceTooltip currency={currency} targetPrice={targetPrice} />}
                cursor={{ stroke: 'var(--ink)', strokeWidth: 1, strokeDasharray: '2 3' }}
              />
              <ReferenceLine
                y={targetPrice}
                stroke="var(--ink)"
                strokeWidth={1.5}
                strokeDasharray="6 4"
                label={{
                  value: `Your price ${formatMoney(targetPrice, currency)}`,
                  position: 'insideBottomLeft',
                  fill: 'var(--ink)',
                  fontSize: 12,
                  fontWeight: 700,
                }}
              />
              <Line
                type="stepAfter"
                dataKey="price"
                stroke="var(--ink)"
                strokeWidth={2.25}
                dot={data.length <= 60 ? <PriceDot targetPrice={targetPrice} /> : false}
                activeDot={{ r: 6, fill: 'var(--sticker)', stroke: 'var(--ink)', strokeWidth: 2 }}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className="receipt-scroll">
          <table className="receipt">
            <caption className="sr-only">Every recorded price, newest first</caption>
            <thead>
              <tr>
                <th scope="col">Checked</th>
                <th scope="col" className="num">
                  Price
                </th>
              </tr>
            </thead>
            <tbody>
              {[...points].reverse().map((point) => (
                <tr key={point.checkedAt} className={point.price <= targetPrice ? 'is-under' : undefined}>
                  <td>{formatDateTime(point.checkedAt)}</td>
                  <td className="num">
                    {formatMoney(point.price, currency)}
                    {point.price <= targetPrice && <span className="receipt-mark"> at your price</span>}
                    {point.availability === 'OUT_OF_STOCK' && (
                      <span className="receipt-mark receipt-mark-out"> sold out</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
