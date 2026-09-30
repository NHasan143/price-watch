import type { Product } from '../api/types'

function moneyFormatter(currency?: string | null): Intl.NumberFormat {
  if (currency) {
    try {
      // narrowSymbol shows ৳ / € / $ like a shelf label; the ISO code is printed separately on the label.
      return new Intl.NumberFormat(undefined, { style: 'currency', currency, currencyDisplay: 'narrowSymbol' })
    } catch {
      // unknown currency code: fall back to a plain number
    }
  }
  return new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function formatMoney(value: number | null | undefined, currency?: string | null): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return moneyFormatter(currency).format(value)
}

export interface PriceParts {
  symbol: string
  whole: string
  fraction: string
  symbolFirst: boolean
}

/** Splits a price into symbol, whole number and fraction so it can be set like a shelf-edge label. */
export function priceParts(value: number, currency?: string | null): PriceParts {
  const parts = moneyFormatter(currency).formatToParts(value)
  const integerIndex = parts.findIndex((part) => part.type === 'integer')
  const symbolIndex = parts.findIndex((part) => part.type === 'currency')
  return {
    symbol: symbolIndex >= 0 ? parts[symbolIndex].value : '',
    whole: parts
      .filter((part) => part.type === 'integer' || part.type === 'group' || part.type === 'minusSign')
      .map((part) => part.value)
      .join(''),
    fraction: parts
      .filter((part) => part.type === 'decimal' || part.type === 'fraction')
      .map((part) => part.value)
      .join(''),
    symbolFirst: symbolIndex < integerIndex,
  }
}

export function timeAgo(iso: string | null | undefined, style: Intl.RelativeTimeFormatStyle = 'long'): string {
  if (!iso) {
    return 'never'
  }
  const seconds = Math.round((Date.parse(iso) - Date.now()) / 1000)
  const magnitude = Math.abs(seconds)
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto', style })
  if (magnitude < 45) return 'just now'
  if (magnitude < 3600) return formatter.format(Math.round(seconds / 60), 'minute')
  if (magnitude < 86400) return formatter.format(Math.round(seconds / 3600), 'hour')
  return formatter.format(Math.round(seconds / 86400), 'day')
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function hostOf(url: string): string {
  try {
    return new URL(url).host.replace(/^www\./, '')
  } catch {
    return url
  }
}

/** How close a price counts as "closing in" on the target (10 % above it). */
export const CLOSING_IN_RATIO = 1.1

export type Status = 'below' | 'closing' | 'watching' | 'error'

/** Shelf order: what needs the shopper's attention first. */
export const STATUS_ORDER: Record<Status, number> = { below: 0, closing: 1, error: 2, watching: 3 }

export function statusOf(product: Product): Status {
  if (product.lastError) return 'error'
  if (product.belowTarget) return 'below'
  if (product.currentPrice !== null && product.currentPrice <= product.targetPrice * CLOSING_IN_RATIO) {
    return 'closing'
  }
  return 'watching'
}
