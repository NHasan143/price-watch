import type { ReactNode } from 'react'

/** The graphite aisle band: wordmark on the left, whatever the page needs on the right. */
export function SiteHeader({ children }: { children?: ReactNode }) {
  return (
    <header className="aisle">
      <div className="aisle-inner">
        <div className="brand">
          <img src="/favicon.svg" alt="" width={40} height={40} />
          <div>
            <h1>PriceWatch</h1>
            <p>Name your price. We’ll wait for it.</p>
          </div>
        </div>
        {children}
      </div>
    </header>
  )
}
