import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import type { Product } from './api/types'
import { AddProductForm } from './components/AddProductForm'
import { EmptyShelf } from './components/EmptyShelf'
import { HistoryRow } from './components/HistoryRow'
import { Icon } from './components/Icon'
import { ProductCard } from './components/ProductCard'
import { useProducts } from './hooks/useProducts'
import { STATUS_ORDER, statusOf, timeAgo } from './utils/format'

/** Keep in sync with the minimum bay width in index.css (.shelf). */
const BAY_MIN_WIDTH = 330

/** Number of bays per shelf row, so a history panel can open below the row without moving anything. */
function useShelfColumns() {
  const ref = useRef<HTMLDivElement>(null)
  const [columns, setColumns] = useState(1)
  useEffect(() => {
    const element = ref.current
    if (!element) return
    const observer = new ResizeObserver(([entry]) =>
      setColumns(Math.max(1, Math.floor(entry.contentRect.width / BAY_MIN_WIDTH))),
    )
    observer.observe(element)
    return () => observer.disconnect()
  })
  return { ref, columns }
}

type Filter = 'all' | 'below' | 'closing' | 'error'

const FILTERS: { id: Filter; label: string; matches: (product: Product) => boolean }[] = [
  { id: 'all', label: 'All', matches: () => true },
  { id: 'below', label: 'At your price', matches: (p) => p.belowTarget },
  { id: 'closing', label: 'Almost there', matches: (p) => statusOf(p) === 'closing' },
  { id: 'error', label: 'Check failed', matches: (p) => p.lastError !== null },
]

function App() {
  const { products, setProducts, loading, error, refresh } = useProducts()
  const [filter, setFilter] = useState<Filter>('all')
  const [historyId, setHistoryId] = useState<number | null>(null)
  const { ref: shelfRef, columns: shelfColumns } = useShelfColumns()

  const belowTarget = products.filter((p) => p.belowTarget).length
  const lastChecked = products
    .map((p) => p.lastCheckedAt)
    .filter((value): value is string => value !== null)
    .sort()
    .at(-1)

  const counts = Object.fromEntries(FILTERS.map((f) => [f.id, products.filter(f.matches).length])) as Record<
    Filter,
    number
  >
  // A filter that no longer matches anything falls back to showing everything.
  const activeFilter = counts[filter] > 0 ? filter : 'all'
  const visible = products
    .filter(FILTERS.find((f) => f.id === activeFilter)!.matches)
    .map((product, order) => ({ product, order }))
    .sort((a, b) => STATUS_ORDER[statusOf(a.product)] - STATUS_ORDER[statusOf(b.product)] || a.order - b.order)
    .map(({ product }) => product)

  const columns = Math.min(shelfColumns, Math.max(visible.length, 1))
  const openIndex = visible.findIndex((p) => p.id === historyId)
  const openRowEnd = openIndex < 0 ? -1 : Math.min((Math.floor(openIndex / columns) + 1) * columns, visible.length) - 1
  const shelfItems: ReactNode[] = []
  visible.forEach((product, index) => {
    shelfItems.push(
      <ProductCard
        key={product.id}
        product={product}
        index={index}
        historyOpen={product.id === historyId}
        onToggleHistory={() => setHistoryId((current) => (current === product.id ? null : product.id))}
        onUpdated={(updated) => setProducts((current) => current.map((p) => (p.id === updated.id ? updated : p)))}
        onDeleted={(id) => setProducts((current) => current.filter((p) => p.id !== id))}
      />,
    )
    if (index === openRowEnd) {
      shelfItems.push(
        <HistoryRow key="history" product={visible[openIndex]} column={openIndex % columns} columns={columns} />,
      )
    }
  })
  return (
    <div className="app">
      <header className="aisle">
        <div className="aisle-inner">
          <div className="brand">
            <img src="/favicon.svg" alt="" width={40} height={40} />
            <div>
              <h1>PriceWatch</h1>
              <p>Name your price. We’ll wait for it.</p>
            </div>
          </div>
          <dl className="readouts">
            <div className="readout">
              <dt>Tracking</dt>
              <dd>{products.length}</dd>
            </div>
            <div className={`readout${belowTarget > 0 ? ' readout-hit' : ''}`}>
              <dt>
                <span className="readout-long">At or below target</span>
                <span className="readout-short" aria-hidden="true">At target</span>
              </dt>
              <dd>{belowTarget}</dd>
            </div>
            <div className="readout">
              <dt>Last check</dt>
              <dd className="readout-time">{lastChecked ? timeAgo(lastChecked, 'short') : '—'}</dd>
            </div>
          </dl>
        </div>
      </header>

      <main className="store">
        <AddProductForm
          onCreated={(product) => {
            setProducts((current) => [product, ...current])
            setFilter('all')
          }}
        />

        {error && (
          <div className="notice notice-void notice-banner" role="alert">
            <Icon name="alert" size={18} />
            <span>{error}</span>
            <button type="button" className="btn btn-ink btn-small" onClick={() => void refresh()}>
              <Icon name="refresh" size={16} />
              Retry
            </button>
          </div>
        )}

        <section className="aisle-section" aria-labelledby="products-heading">
          <div className="shelf-head">
            <h2 id="products-heading" className={products.length === 0 && !loading ? 'sr-only' : undefined}>
              Your products
            </h2>
            {products.length > 0 && (
              <div className="filters" role="group" aria-label="Show products">
                {FILTERS.filter((f) => f.id === 'all' || counts[f.id] > 0).map((f) => (
                  <button
                    key={f.id}
                    type="button"
                    className={`filter filter-${f.id}`}
                    aria-pressed={activeFilter === f.id}
                    onClick={() => setFilter(f.id)}
                  >
                    {f.label}
                    <span className="filter-count">{counts[f.id]}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          {loading ? (
            <div className="shelf" aria-busy="true">
              {[0, 1, 2].map((i) => (
                <div key={i} className="bay bay-ghost" aria-hidden="true">
                  <div className="bay-unit">
                    <div className="bay-display" />
                    <div className="rail">
                      <div className="label label-ghost" />
                    </div>
                  </div>
                </div>
              ))}
              <p className="sr-only">Loading your products…</p>
            </div>
          ) : products.length === 0 && !error ? (
            <EmptyShelf
              onStart={() => {
                const input = document.getElementById('product-url')
                input?.scrollIntoView({ behavior: 'smooth', block: 'center' })
                input?.focus({ preventScroll: true })
              }}
            />
          ) : (
            <div className="shelf" ref={shelfRef}>
              {shelfItems}
            </div>
          )}
        </section>
      </main>

      <footer className="site-footer">
        <div className="site-footer-inner">
          <p>Prices are read from public product pages. Check a store’s terms before tracking it.</p>
          <p className="credit">
            Created with
            <svg className="credit-heart" viewBox="0 0 24 24" width="16" height="16" role="img" aria-label="love">
              <path d="M12 20.5s-7.5-4.6-9.2-9.4C1.6 7.6 3.8 4.5 7.1 4.5c2 0 3.6 1.1 4.9 2.9 1.3-1.8 2.9-2.9 4.9-2.9 3.3 0 5.5 3.1 4.3 6.6-1.7 4.8-9.2 9.4-9.2 9.4z" />
            </svg>
            by{' '}
            <a href="https://www.linkedin.com/in/naymulhasan143/" target="_blank" rel="noopener noreferrer">
              Naymul Hasan<span className="sr-only"> (LinkedIn, opens in a new tab)</span>
            </a>
          </p>
        </div>
      </footer>
    </div>
  )
}

export default App
