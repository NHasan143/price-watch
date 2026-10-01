import { useState } from 'react'
import type { CSSProperties, FormEvent } from 'react'
import { api, errorMessage } from '../api/client'
import type { Product } from '../api/types'
import { failureCopy } from '../utils/failure'
import { formatMoney, hostOf, statusOf, timeAgo } from '../utils/format'
import { Barcode } from './Barcode'
import { FailureNotice } from './FailureNotice'
import { Icon } from './Icon'
import { LabelPrice } from './LabelPrice'

interface Props {
  product: Product
  /** Position on the shelf, used to stagger the sticker animation. */
  index: number
  historyOpen: boolean
  onToggleHistory: () => void
  onUpdated: (product: Product) => void
  onDeleted: (id: number) => void
}

type Busy = 'check' | 'save' | 'delete' | null

export function ProductCard({ product, index, historyOpen, onToggleHistory, onUpdated, onDeleted }: Props) {
  const [busy, setBusy] = useState<Busy>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [targetDraft, setTargetDraft] = useState('')
  const [imageFailed, setImageFailed] = useState(false)

  // Reprint the price (a short print animation) whenever a check records a different price.
  const [shownPrice, setShownPrice] = useState(product.currentPrice)
  const [printCount, setPrintCount] = useState(0)
  if (product.currentPrice !== shownPrice) {
    setShownPrice(product.currentPrice)
    setPrintCount((count) => count + 1)
  }

  const status = statusOf(product)
  const currency = product.currency
  const gap = product.currentPrice === null ? null : product.currentPrice - product.targetPrice
  const nameId = `product-${product.id}-name`
  const failure = product.lastError ? failureCopy(product) : null

  async function checkNow() {
    setBusy('check')
    setError(null)
    try {
      onUpdated(await api.checkNow(product.id))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(null)
    }
  }

  async function saveTarget(event: FormEvent) {
    event.preventDefault()
    const target = Number(targetDraft)
    if (!Number.isFinite(target) || target <= 0) {
      setError('Enter a target price greater than zero.')
      return
    }
    setBusy('save')
    setError(null)
    try {
      onUpdated(await api.updateProduct(product.id, { targetPrice: target }))
      setEditing(false)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(null)
    }
  }

  async function remove() {
    if (!window.confirm(`Stop tracking "${product.name}"? Its price history will be deleted.`)) {
      return
    }
    setBusy('delete')
    setError(null)
    try {
      await api.deleteProduct(product.id)
      onDeleted(product.id)
    } catch (e) {
      setError(errorMessage(e))
      setBusy(null)
    }
  }

  return (
    <article
      className={`bay${historyOpen ? ' bay-open' : ''}`}
      data-status={status}
      aria-labelledby={nameId}
      style={{ '--i': index } as CSSProperties}
    >
      <div className="bay-unit">
        <div
          className={`bay-display${product.belowTarget || status === 'closing' ? ' has-signage' : ''}`}
        >
          {product.imageUrl && !imageFailed ? (
            <span className="bay-tile">
              <img
                className="bay-product"
                src={product.imageUrl}
                alt=""
                loading="lazy"
                onError={() => setImageFailed(true)}
              />
            </span>
          ) : (
            <span
              className="bay-product bay-product-blank"
              aria-hidden="true"
              style={{ '--chars': Math.max(hostOf(product.url).length, 6) } as CSSProperties}
            >
              {hostOf(product.url)}
            </span>
          )}

          {product.belowTarget && product.currentPrice !== null && (
            <p className="sticker">
              <span className="sticker-head">At your price</span>
              <span className="sticker-line">
                {gap !== null && gap < 0 ? `${formatMoney(-gap, currency)} under` : 'Right on target'}
              </span>
            </p>
          )}
          {status === 'closing' && gap !== null && (
            <p className="wobbler">
              <span className="wobbler-head">Almost</span>
              <span className="wobbler-line">{formatMoney(gap, currency)}</span>
              <span className="wobbler-sub">to go</span>
            </p>
          )}
        </div>

        <div className="rail">
          <div className={`label${busy === 'check' ? ' is-scanning' : ''}${product.lastError ? ' is-void' : ''}`}>
            <div className="label-top">
              <div className="label-name">
                <h3 id={nameId}>
                  <a href={product.url} target="_blank" rel="noopener noreferrer" title={product.name}>
                    {product.name}
                    <span className="sr-only"> (opens the shop page)</span>
                  </a>
                </h3>
                <span className="label-shop">{hostOf(product.url)}</span>
              </div>
              <div className="label-price-slot">
                {product.lastError && <span className="label-void-tag">Last read</span>}
                {product.availability === 'OUT_OF_STOCK' && <span className="label-void-tag label-stamp-tag">Sold out</span>}
                {product.availability === 'PREORDER' && <span className="label-void-tag">Pre-order</span>}
                <LabelPrice
                key={printCount}
                value={product.currentPrice}
                currency={currency}
                className={`label-price-now${printCount > 0 ? ' is-reprinted' : ''}`}
                />
              </div>
            </div>

            {editing ? (
              <form className="label-edit" onSubmit={saveTarget}>
                <label>
                  <span className="label-edit-caption">New target</span>
                  <input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    min="0.01"
                    value={targetDraft}
                    onChange={(e) => setTargetDraft(e.target.value)}
                    autoFocus
                    required
                  />
                </label>
                <button type="submit" className="btn btn-ink btn-small" disabled={busy === 'save'}>
                  <Icon name="check" size={16} />
                  {busy === 'save' ? 'Saving…' : 'Save'}
                </button>
                <button type="button" className="btn btn-small" onClick={() => setEditing(false)}>
                  Cancel
                </button>
              </form>
            ) : (
              <p className="label-target">
                <span>
                  Your price <strong>{formatMoney(product.targetPrice, currency)}</strong>
                </span>
                {gap !== null && (
                  <span className={`label-gap label-gap-${gap <= 0 ? 'under' : 'over'}`}>
                    {gap <= 0 ? `${formatMoney(-gap, currency)} under` : `${formatMoney(gap, currency)} to go`}
                  </span>
                )}
              </p>
            )}

            <dl className="label-range">
              <div>
                <dt>Lowest</dt>
                <dd>{formatMoney(product.lowestPrice, currency)}</dd>
              </div>
              <div>
                <dt>Highest</dt>
                <dd>{formatMoney(product.highestPrice, currency)}</dd>
              </div>
            </dl>

            <div className="label-foot">
              <Barcode seed={`${product.id}${product.url}`} />
              <span className="label-code">
                #{String(product.id).padStart(4, '0')}
                {currency && ` · ${currency}`}
                {busy === 'check' ? ' · checking…' : ` · checked ${timeAgo(product.lastCheckedAt)}`}
              </span>
            </div>
          </div>
        </div>

        {product.lastError && failure && (
          <FailureNotice
            icon={failure.icon}
            title={failure.title}
            detail={product.lastError}
            next={failure.next}
            role="status"
          />
        )}
        {error && (
          <p className="notice notice-void" role="alert">
            <Icon name="alert" size={16} />
            <span>{error}</span>
          </p>
        )}

        <div className="bay-actions">
          <button type="button" className="btn btn-ghost" onClick={checkNow} disabled={busy !== null}>
            <Icon name="refresh" size={16} />
            {busy === 'check' ? 'Checking…' : 'Check now'}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            aria-expanded={historyOpen}
            aria-controls={`history-${product.id}`}
            aria-label={historyOpen ? 'Hide history' : undefined}
            onClick={onToggleHistory}
          >
            <Icon name={historyOpen ? 'close' : 'history'} size={16} />
            {historyOpen ? 'Hide' : 'History'}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={busy !== null || editing}
            onClick={() => {
              setTargetDraft(product.targetPrice.toFixed(2))
              setEditing(true)
            }}
          >
            <Icon name="pencil" size={16} />
            <span aria-hidden="true">Edit</span>
            <span className="sr-only">Edit target price</span>
          </button>
          <button type="button" className="btn btn-ghost btn-danger" onClick={remove} disabled={busy !== null}>
            <Icon name="trash" size={16} />
            {busy === 'delete' ? 'Deleting…' : 'Delete'}
          </button>
        </div>
      </div>
    </article>
  )
}
