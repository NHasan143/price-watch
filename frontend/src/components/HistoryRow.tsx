import { lazy, Suspense } from 'react'
import type { CSSProperties } from 'react'
import type { Product } from '../api/types'

// The chart library is the biggest dependency, so it is only downloaded when a history panel is opened.
const PriceChart = lazy(() => import('./PriceChart').then((module) => ({ default: module.PriceChart })))

/** Full-width history panel, inserted after the shelf row of the product it belongs to. */
export function HistoryRow({ product, column, columns }: { product: Product; column: number; columns: number }) {
  return (
    <div
      className="history-row"
      id={`history-${product.id}`}
      style={{ '--notch': `${((column + 0.5) / columns) * 100}%` } as CSSProperties}
    >
      <Suspense fallback={<p className="history history-loading">Loading chart…</p>}>
        <PriceChart
          productId={product.id}
          productName={product.name}
          targetPrice={product.targetPrice}
          currency={product.currency}
          version={product.lastCheckedAt}
        />
      </Suspense>
    </div>
  )
}
