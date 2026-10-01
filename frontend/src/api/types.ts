export type Availability = 'IN_STOCK' | 'OUT_OF_STOCK' | 'PREORDER' | 'UNKNOWN'

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
  /** As of the last successful check; null for products added before availability was tracked. */
  availability: Availability | null
  lastCheckedAt: string | null
  lastError: string | null
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
