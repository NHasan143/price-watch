import type { CreateProductInput, FailureReason, Product } from '@web/api/types'
import { API_URL } from './config'
import { guestId } from './guest'

/** The same problem details the web app reads (frontend/src/api/client.ts). */
export class ApiError extends Error {
  status: number
  title?: string
  reason?: FailureReason | 'SIGN_UP_REQUIRED'

  constructor(message: string, status: number, title?: string, reason?: FailureReason | 'SIGN_UP_REQUIRED') {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.reason = reason
  }
}

/** Status of an ApiError for a request that never reached the API. */
export const UNREACHABLE = 0

export type TokenSource = () => Promise<string | null>

async function request<T>(path: string, token: TokenSource, init?: RequestInit): Promise<T> {
  // Signed in: the Clerk session token. Always: the guest id, so a guest's products are found.
  const [session, guest] = await Promise.all([token().catch(() => null), guestId()])
  let response: Response
  try {
    response = await fetch(API_URL + path, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        'X-Guest-Id': guest,
        ...(session ? { Authorization: `Bearer ${session}` } : {}),
      },
    })
  } catch {
    throw new ApiError(`Nothing answered at ${API_URL}. Start the PriceWatch backend, then try again.`, UNREACHABLE)
  }

  if (response.status === 401) {
    throw new ApiError('Your session has ended. Sign in again on PriceWatch.', 401)
  }
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`.trim()
    let title: string | undefined
    let reason: FailureReason | 'SIGN_UP_REQUIRED' | undefined
    try {
      const problem = (await response.json()) as { detail?: string; title?: string; reason?: typeof reason }
      message = problem.detail ?? problem.title ?? message
      title = problem.title
      reason = problem.reason
    } catch {
      // body was not JSON: keep the status text
    }
    throw new ApiError(message, response.status, title, reason)
  }
  return (await response.json()) as T
}

export const api = {
  listProducts: (token: TokenSource) => request<Product[]>('/api/products', token),

  createProduct: (token: TokenSource, input: CreateProductInput) =>
    request<Product>('/api/products', token, { method: 'POST', body: JSON.stringify(input) }),
}

/** Compares product addresses the way a shopper would: scheme, "www.", trailing slash and fragment don't matter. */
export function sameProduct(a: string, b: string): boolean {
  const key = (url: string) => {
    try {
      const parsed = new URL(url)
      return `${parsed.hostname.replace(/^www\./, '')}${parsed.pathname.replace(/\/+$/, '')}${parsed.search}`
    } catch {
      return url
    }
  }
  return key(a) === key(b)
}
