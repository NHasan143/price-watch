import { priceParts } from '../utils/format'
import type { PriceParts } from '../utils/format'

/** A price set like a shelf-edge label: small symbol, heavy whole number, raised fraction. */
export function LabelPrice({
  value,
  currency,
  className = '',
}: {
  value: number | null
  currency: string | null
  className?: string
}) {
  if (value === null) {
    return <span className={`label-price label-price-empty ${className}`}>—</span>
  }
  const parts: PriceParts = priceParts(value, currency)
  const symbol = parts.symbol && <span className="label-price-symbol">{parts.symbol}</span>
  return (
    <span className={`label-price ${className}`}>
      {parts.symbolFirst && symbol}
      <span className="label-price-whole">{parts.whole}</span>
      {parts.fraction && <span className="label-price-fraction">{parts.fraction}</span>}
      {!parts.symbolFirst && symbol}
    </span>
  )
}
