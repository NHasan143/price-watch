import type { CreateProductInput, PricePoint, Product } from './types'

// Empty by default: requests go to /api on the same origin and the Vite dev server proxies them
// to the backend (see vite.config.ts). Set VITE_API_BASE_URL when hosting the API elsewhere.
const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(BASE_URL + path, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
  } catch {
    throw new ApiError('Cannot reach the PriceWatch API. Is the backend running on port 8080?', 0)
  }

  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`.trim()
    try {
      // The backend answers errors as RFC 7807 problem details.
      const problem = (await response.json()) as { detail?: string; title?: string }
      message = problem.detail ?? problem.title ?? message
    } catch {
      // body was not JSON: keep the status text
    }
    throw new ApiError(message, response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const api = {
  listProducts: () => request<Product[]>('/api/products'),

  createProduct: (input: CreateProductInput) =>
    request<Product>('/api/products', { method: 'POST', body: JSON.stringify(input) }),

  updateProduct: (id: number, input: { name?: string; targetPrice: number }) =>
    request<Product>(`/api/products/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  deleteProduct: (id: number) => request<void>(`/api/products/${id}`, { method: 'DELETE' }),

  checkNow: (id: number) => request<Product>(`/api/products/${id}/check`, { method: 'POST' }),

  history: (id: number) => request<PricePoint[]>(`/api/products/${id}/history`),
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong.'
}
