export type Availability = 'IN_STOCK' | 'OUT_OF_STOCK' | 'PREORDER' | 'UNKNOWN'

/** Why a check failed: BLOCKED shops refuse automated checks; TEMPORARY failures are retried early. */
export type FailureReason = 'BLOCKED' | 'TEMPORARY' | 'PAGE_GONE' | 'NO_PRICE' | 'INVALID'

export interface Product {
  id: number
  name: string
  url: string
  imageUrl: string | null
  currency: string | null
  targetPrice: number
  currentPrice: number | null
  lowestPrice: number | null
  highestPrice: number | null
  belowTarget: boolean
  /** False for a guest's product: read once, not re-checked, no alerts until they sign up. */
  tracked: boolean
  /** As of the last successful check; null for products added before availability was tracked. */
  availability: Availability | null
  lastCheckedAt: string | null
  lastError: string | null
  /** Null after a successful check, and for failures recorded before reasons were tracked. */
  lastErrorReason: FailureReason | null
  /** Checks that failed in a row since the last successful one. */
  failedChecks: number
  /** When a temporarily failed check is tried again ahead of the regular schedule. */
  nextRetryAt: string | null
  createdAt: string
}

export interface PricePoint {
  checkedAt: string
  price: number
  availability: Availability | null
}

export interface CreateProductInput {
  url: string
  targetPrice: number
  name?: string
  cssSelector?: string
}
