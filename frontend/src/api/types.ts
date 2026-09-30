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
  lastCheckedAt: string | null
  lastError: string | null
  createdAt: string
}

export interface PricePoint {
  checkedAt: string
  price: number
}

export interface CreateProductInput {
  url: string
  targetPrice: number
  name?: string
  cssSelector?: string
}
