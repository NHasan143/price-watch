import { sessionToken } from '../auth/accounts'
import { guestId } from '../utils/guest'
import type { CreateProductInput, FailureReason, PricePoint, Product } from './types'

// Empty by default: requests go to /api on the same origin and the Vite dev server proxies them
// to the backend (see vite.config.ts). Set VITE_API_BASE_URL when hosting the API elsewhere.
const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  status: number
  /** Short problem title, such as "The shop blocks automated price checks". */
  title?: string
  /** Why reading the shop's page failed, or SIGN_UP_REQUIRED when only an account may do this. */
  reason?: FailureReason | 'SIGN_UP_REQUIRED'

  constructor(message: string, status: number, title?: string, reason?: FailureReason | 'SIGN_UP_REQUIRED') {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.reason = reason
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // Signed in: the Clerk session token (Clerk refreshes it before it expires). Always: the guest id,
  // so a guest's products can be found, and claimed when they sign up.
  const token = await sessionToken()
  let response: Response
  try {
    response = await fetch(BASE_URL + path, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        'X-Guest-Id': guestId(),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init?.headers,
      },
    })
  } catch {
    throw new ApiError('Cannot reach the PriceWatch API. Is the backend running on port 8080?', 0)
  }

  if (response.status === 401) {
    throw new ApiError('Your session has ended. Sign in again to see your shelf.', 401)
  }

  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`.trim()
    let title: string | undefined
    let reason: FailureReason | 'SIGN_UP_REQUIRED' | undefined
    try {
      // The backend answers errors as RFC 7807 problem details.
      const problem = (await response.json()) as {
        detail?: string
        title?: string
        reason?: FailureReason | 'SIGN_UP_REQUIRED'
      }
      message = problem.detail ?? problem.title ?? message
      title = problem.title
      reason = problem.reason
    } catch {
      // body was not JSON: keep the status text
    }
    throw new ApiError(message, response.status, title, reason)
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

  /** Moves this browser's guest products to the signed-in account. */
  claimGuestProducts: () => request<{ claimed: number }>('/api/guest/claim', { method: 'POST' }),
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong.'
}
