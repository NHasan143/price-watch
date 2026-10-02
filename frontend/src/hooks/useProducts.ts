import { useCallback, useEffect, useState } from 'react'
import { api, errorMessage } from '../api/client'
import type { Product } from '../api/types'

/** Loads the tracked products and keeps them fresh by polling the API. */
export function useProducts(enabled = true, pollIntervalMs = 60_000) {
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(
    () =>
      api
        .listProducts()
        .then((list) => {
          setProducts(list)
          setError(null)
        })
        .catch((e: unknown) => setError(errorMessage(e)))
        .finally(() => setLoading(false)),
    [],
  )

  useEffect(() => {
    if (!enabled) return
    void refresh()
    const timer = window.setInterval(() => void refresh(), pollIntervalMs)
    return () => window.clearInterval(timer)
  }, [refresh, pollIntervalMs, enabled])

  return { products, setProducts, loading, error, refresh }
}
