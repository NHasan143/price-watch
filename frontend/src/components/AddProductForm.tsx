import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, errorMessage } from '../api/client'
import type { Product } from '../api/types'
import { Barcode } from './Barcode'
import { Icon } from './Icon'

interface Props {
  onCreated: (product: Product) => void
}

export function AddProductForm({ onCreated }: Props) {
  const [url, setUrl] = useState('')
  const [targetPrice, setTargetPrice] = useState('')
  const [name, setName] = useState('')
  const [cssSelector, setCssSelector] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const created = await api.createProduct({
        url: url.trim(),
        targetPrice: Number(targetPrice),
        name: name.trim() || undefined,
        cssSelector: cssSelector.trim() || undefined,
      })
      onCreated(created)
      setUrl('')
      setTargetPrice('')
      setName('')
      setCssSelector('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="printer" aria-labelledby="add-heading">
      <div className="printer-intro">
        <h2 id="add-heading">Track a product</h2>
        <p>Paste a product page from any shop and the price you want to pay. PriceWatch reads the price the shop publishes and checks it again every few hours.</p>
      </div>

      <div className="printer-sheet">
        <form onSubmit={submit} className={`label label-blank${submitting ? ' is-scanning' : ''}`} aria-busy={submitting}>
          <div className="blank-grid">
            <label className="blank-field blank-url">
              <span className="blank-caption">Product page link</span>
              <input
                id="product-url"
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://shop.example/product/123"
                autoComplete="off"
                spellCheck={false}
                required
              />
            </label>

            <label className="blank-field blank-price">
              <span className="blank-caption">Alert me at or below</span>
              <input
                type="number"
                inputMode="decimal"
                step="0.01"
                min="0.01"
                value={targetPrice}
                onChange={(e) => setTargetPrice(e.target.value)}
                placeholder="0.00"
                required
              />
            </label>

            <label className="blank-field blank-name">
              <span className="blank-caption">
                Name <em>optional</em>
              </span>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Taken from the page if empty"
                maxLength={255}
              />
            </label>

            <div className="blank-submit">
              <button type="submit" className="btn btn-ink btn-large" disabled={submitting}>
                <Icon name="plus" size={18} />
                {submitting ? 'Reading the page…' : 'Start tracking'}
              </button>
            </div>
          </div>

          <details className="blank-advanced">
            <summary>Advanced: the shop doesn’t publish its price?</summary>
            <label className="blank-field">
              <span className="blank-caption">CSS selector for the price element</span>
              <input
                type="text"
                value={cssSelector}
                onChange={(e) => setCssSelector(e.target.value)}
                placeholder=".product-price .amount"
                spellCheck={false}
                maxLength={255}
              />
            </label>
          </details>

          <div className="label-foot blank-foot" aria-hidden="true">
            <Barcode seed={url || 'new label'} />
            <span className="label-code">New label · price and currency are read from the shop</span>
          </div>
        </form>
      </div>

      {error && (
        <p className="notice notice-void" role="alert">
          <Icon name="alert" size={16} />
          <span>{error}</span>
        </p>
      )}
    </section>
  )
}
