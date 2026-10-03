import { useEffect, useState } from 'react'
import type { CSSProperties, FormEvent } from 'react'
import type { Product } from '@web/api/types'
import { Barcode } from '@web/components/Barcode'
import { Icon } from '@web/components/Icon'
import type { IconName } from '@web/components/Icon'
import { LabelPrice } from '@web/components/LabelPrice'
import { formatMoney, hostOf } from '@web/utils/format'
import { ApiError, api, sameProduct, UNREACHABLE } from '../api'
import { APP_URL } from '../config'
import { readProductPage } from '../readPage'
import type { PageReading } from '../readPage'
import { useAccount } from './account'

/** Markdowns offered next to the price field; the first suggestion is 10 % below today's price. */
const CUTS = [5, 10, 15, 20]
const DEFAULT_CUT = 10

/** The laser gets at least this long on the label, so the price visibly prints rather than flickers in. */
const MIN_SCAN_MS = 450

type Page = { status: 'reading' } | { status: 'unsupported' } | { status: 'read'; reading: PageReading }

type Shelf =
  | { status: 'waiting' }
  | { status: 'ready'; existing: Product | null }
  | { status: 'failed'; error: ApiError }

interface Problem {
  icon: IconName
  title: string
  detail: string
  next?: string
  action?: 'retry' | 'signUp' | 'signIn'
  /** Adding again can't work (the guest limit, a shop that blocks checks): the Add button goes away. */
  final?: boolean
}

type Watch = { status: 'idle' } | { status: 'adding' } | { status: 'added'; product: Product } | { status: 'failed'; problem: Problem }

function toProblem(error: unknown): Problem {
  if (!(error instanceof ApiError)) {
    return { icon: 'alert', title: 'Something went wrong', detail: error instanceof Error ? error.message : 'Try again.' }
  }
  if (error.status === UNREACHABLE) {
    return { icon: 'clock', title: 'Can’t reach PriceWatch', detail: error.message, action: 'retry' }
  }
  if (error.status === 401) {
    return { icon: 'alert', title: 'Signed out', detail: error.message, action: 'signIn' }
  }
  const title = error.title ?? 'Couldn’t add this product'
  switch (error.reason) {
    case 'SIGN_UP_REQUIRED':
      return { icon: 'tag', title, detail: error.message, action: 'signUp', final: true }
    case 'BLOCKED':
      return {
        icon: 'blocked',
        title,
        detail: error.message,
        final: true,
        next: 'PriceWatch can’t track this shop. If another shop sells the same product, try that page.',
      }
    case 'TEMPORARY':
      return { icon: 'clock', title, detail: error.message, next: 'Nothing was saved. Try again in a minute.' }
    default:
      return { icon: 'alert', title, detail: error.message }
  }
}

function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** Reads the product from the shop's tab the popup was opened on. */
function usePage(): Page {
  const [page, setPage] = useState<Page>({ status: 'reading' })
  useEffect(() => {
    let cancelled = false
    const started = performance.now()
    async function read(): Promise<Page> {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
      if (tab?.id === undefined || !/^https?:/.test(tab.url ?? '')) {
        return { status: 'unsupported' }
      }
      try {
        const [result] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: readProductPage })
        return result?.result ? { status: 'read', reading: result.result } : { status: 'unsupported' }
      } catch {
        // pages Chrome keeps extensions out of (the Web Store, other extensions, PDFs)
        return { status: 'unsupported' }
      }
    }
    void read().then(async (next) => {
      const remaining = prefersReducedMotion() ? 0 : MIN_SCAN_MS - (performance.now() - started)
      if (remaining > 0) await new Promise((resolve) => setTimeout(resolve, remaining))
      if (!cancelled) setPage(next)
    })
    return () => {
      cancelled = true
    }
  }, [])
  return page
}

/** Looks the page up on the shopper's shelf, so a product already watched isn't added twice. */
function useShelf(url: string | null, attempt: number): Shelf {
  const account = useAccount()
  // the lookup this result answers; a new page, account or retry means waiting for a new one
  const key = url === null || account.status === 'loading' ? null : `${account.status} ${attempt} ${url}`
  const [result, setResult] = useState<{ key: string; shelf: Shelf } | null>(null)
  useEffect(() => {
    if (key === null || url === null) return
    let cancelled = false
    api
      .listProducts(account.token)
      .then((products) => ({ status: 'ready', existing: products.find((p) => sameProduct(p.url, url)) ?? null }) as const)
      .catch((error: unknown) => ({
        status: 'failed' as const,
        error: error instanceof ApiError ? error : new ApiError(String(error), UNREACHABLE),
      }))
      .then((shelf) => {
        if (!cancelled) setResult({ key, shelf })
      })
    return () => {
      cancelled = true
    }
  }, [key, url, account.token])
  return result?.key === key ? result.shelf : { status: 'waiting' }
}

function markdown(price: number, cut: number): string {
  return (Math.round(price * (100 - cut)) / 100).toFixed(2)
}

export function Popup() {
  const account = useAccount()
  const page = usePage()
  const reading = page.status === 'read' ? page.reading : null
  const [attempt, setAttempt] = useState(0)
  const shelf = useShelf(reading?.url ?? null, attempt)
  const [watch, setWatch] = useState<Watch>({ status: 'idle' })
  const [cut, setCut] = useState<number | null>(DEFAULT_CUT)
  const [target, setTarget] = useState('')

  // Suggest a price once the page has been read: 10 % below what the shop asks today.
  const [suggestedFor, setSuggestedFor] = useState<PageReading | null>(null)
  if (reading && reading !== suggestedFor) {
    setSuggestedFor(reading)
    setTarget(reading.price === null ? '' : markdown(reading.price, DEFAULT_CUT))
    setCut(reading.price === null ? null : DEFAULT_CUT)
  }

  const onShelf = watch.status === 'added' ? watch.product : shelf.status === 'ready' ? shelf.existing : null
  const scanning = page.status === 'reading' || watch.status === 'adding'
  const currency = onShelf?.currency ?? reading?.currency ?? null
  const host = reading ? hostOf(reading.url) : null
  const targetValue = Number(target)
  const targetValid = target.trim() !== '' && Number.isFinite(targetValue) && targetValue >= 0.01
  const signedIn = account.status === 'signedIn'

  function pickCut(next: number) {
    if (reading?.price == null) return
    setCut(next)
    setTarget(markdown(reading.price, next))
  }

  function typeTarget(value: string) {
    if (watch.status === 'failed') setWatch({ status: 'idle' })
    setTarget(value)
    const match = reading?.price == null ? undefined : CUTS.find((c) => markdown(reading.price!, c) === Number(value).toFixed(2))
    setCut(match ?? null)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!reading || !targetValid) return
    setWatch({ status: 'adding' })
    try {
      const product = await api.createProduct(account.token, {
        url: reading.url,
        targetPrice: Number(targetValue.toFixed(2)),
        name: reading.name ?? undefined,
      })
      setWatch({ status: 'added', product })
    } catch (error) {
      setWatch({ status: 'failed', problem: toProblem(error) })
    }
  }

  function retry() {
    setWatch({ status: 'idle' })
    setAttempt((n) => n + 1)
  }

  const shelfProblem = shelf.status === 'failed' ? toProblem(shelf.error) : null
  // A product that can't be added (the shop blocks checks): the price field has nothing left to do.
  const deadEnd = watch.status === 'failed' && watch.problem.final && watch.problem.action !== 'signUp' ? watch.problem : null
  // Today's price is already at or below the shopper's: the markdown sticker lands on the bay.
  const stickered = onShelf !== null && reading?.price != null && reading.price <= onShelf.targetPrice

  return (
    <div className="popup">
      <header className="aisle popup-aisle">
        <a className="popup-wordmark" href={`${APP_URL}/`} target="_blank" rel="noreferrer" title="Open your shelf">
          PriceWatch
        </a>
        <AccountSlot />
      </header>

      <main>
        <section className="popup-bay" aria-label="This page">
          <div className="bay-display popup-display">
            {reading && <ProductImage key={reading.url} reading={reading} />}
            {stickered && reading?.price != null && onShelf && (
              <p className="sticker popup-sticker">
                <span className="sticker-head">At your price</span>
                <span className="sticker-line">
                  {reading.price < onShelf.targetPrice
                    ? `${formatMoney(onShelf.targetPrice - reading.price, reading.currency)} under`
                    : 'Right on target'}
                </span>
              </p>
            )}
          </div>
          <div className="rail">
            <article
              className={`label popup-label${scanning ? ' is-scanning' : ''}${page.status === 'reading' ? ' is-reading' : ''}`}
              aria-busy={scanning}
            >
              <div className="label-top">
                <div className="label-name">
                  {page.status === 'reading' && (
                    <>
                      <h1 className="sr-only">Reading this page</h1>
                      <span className="popup-skeleton" aria-hidden="true" />
                    </>
                  )}
                  {page.status === 'unsupported' && (
                    <>
                      <h1>Nothing to read here</h1>
                      <span className="label-shop">This isn’t a shop’s product page.</span>
                    </>
                  )}
                  {reading && (
                    <>
                      <h1 title={reading.name ?? undefined}>{onShelf?.name ?? reading.name ?? host}</h1>
                      <span className="label-shop">{host}</span>
                    </>
                  )}
                </div>
                <div className="label-price-slot">
                  {onShelf && <span className="label-void-tag popup-shelf-tag">On your shelf</span>}
                  {reading?.price != null ? (
                    <LabelPrice value={reading.price} currency={reading.currency} className="label-price-now is-reprinted" />
                  ) : (
                    <span className="label-price label-price-now label-price-empty" aria-label="No price">
                      —
                    </span>
                  )}
                </div>
              </div>
              <div className="label-foot" aria-hidden="true">
                <Barcode seed={reading?.url ?? 'pricewatch'} />
                <span className="label-code">{labelCode(page, onShelf, currency)}</span>
              </div>
            </article>
          </div>
        </section>

        {page.status === 'unsupported' ? (
          <div className="desk">
            <p className="desk-hint">Open a product page in any shop, then click the PriceWatch icon to see its price and watch it.</p>
            <a className="btn btn-ink btn-large desk-action" href={`${APP_URL}/`} target="_blank" rel="noreferrer">
              Open your shelf
              <Icon name="external" size={16} />
            </a>
          </div>
        ) : onShelf ? (
          <OnShelf
            product={onShelf}
            price={reading?.price ?? onShelf.currentPrice}
            stickered={stickered}
            justAdded={watch.status === 'added'}
          />
        ) : shelfProblem ? (
          // PriceWatch can't be reached (or the session ended): nothing here would work until it can.
          <div className="desk">
            <ProblemNotice problem={shelfProblem} onRetry={retry} />
          </div>
        ) : deadEnd ? (
          <div className="desk">
            <ProblemNotice problem={deadEnd} onRetry={retry} />
            <a className="btn btn-ink btn-large desk-action" href={`${APP_URL}/`} target="_blank" rel="noreferrer">
              Open your shelf
              <Icon name="external" size={16} />
            </a>
          </div>
        ) : (
          <form className="desk" onSubmit={submit} aria-label="Watch this price">
            <label className="desk-field">
              <span className="desk-caption">Alert me at or below</span>
              <span className={`desk-price${target.length > 9 ? ' is-long' : ''}`}>
                {symbolOf(currency) && (
                  <span className="desk-symbol" aria-hidden="true">
                    {symbolOf(currency)}
                  </span>
                )}
                <input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0.01"
                  value={target}
                  onChange={(e) => typeTarget(e.target.value)}
                  placeholder="0.00"
                  disabled={!reading || watch.status === 'adding'}
                  required
                  aria-describedby={watch.status === 'failed' ? undefined : 'desk-drop'}
                />
                {currency && <span className="desk-currency">{currency}</span>}
              </span>
            </label>

            {watch.status === 'failed' ? (
              // in place of the markdowns, so the popup stays one screen tall
              <ProblemNotice problem={watch.problem} onRetry={retry} />
            ) : (
              <>
                {reading?.price != null && (
                  <div className="desk-cuts" role="group" aria-label="Below today’s price">
                    {CUTS.map((c) => (
                      <button
                        key={c}
                        type="button"
                        className="filter desk-cut"
                        aria-pressed={cut === c}
                        onClick={() => pickCut(c)}
                        disabled={watch.status === 'adding'}
                      >
                        −{c}%
                      </button>
                    ))}
                  </div>
                )}

                <p className="desk-drop" id="desk-drop">
                  <DropLine reading={reading} target={targetValid ? targetValue : null} />
                </p>
              </>
            )}

            {watch.status === 'failed' && watch.problem.action === 'signUp' ? (
              <a className="btn btn-ink btn-large desk-action" href={`${APP_URL}/`} target="_blank" rel="noreferrer">
                Sign up to watch more
                <Icon name="external" size={16} />
              </a>
            ) : (
              <button
                type="submit"
                className="btn btn-ink btn-large desk-action"
                disabled={!reading || !targetValid || watch.status === 'adding'}
              >
                {reading && <Icon name={watch.status === 'adding' ? 'refresh' : 'plus'} size={18} />}
                {watch.status === 'adding'
                  ? 'Adding to your shelf…'
                  : page.status === 'reading'
                    ? 'Reading the page…'
                    : signedIn
                      ? 'Watch this price'
                      : 'Add to my shelf'}
              </button>
            )}

            <div className="label-foot desk-foot" aria-hidden="true">
              <Barcode seed={`new ${reading?.url ?? 'label'}`} />
              <span className="label-code">New label · {signedIn ? 'checked twice a day' : 'read once for guests'}</span>
            </div>
          </form>
        )}
      </main>
    </div>
  )
}

function labelCode(page: Page, onShelf: Product | null, currency: string | null): string {
  if (page.status !== 'read') return ''
  if (onShelf) return `#${String(onShelf.id).padStart(4, '0')} · ${currency ?? '—'}`
  if (page.reading.price === null) return 'No price published on this page'
  return `${currency ?? 'Price'} · read from this page`
}

/** The currency's narrow symbol (৳, €, $) for the field; empty when the page names no currency. */
function symbolOf(currency: string | null): string {
  if (!currency) return ''
  try {
    return (
      new Intl.NumberFormat(undefined, { style: 'currency', currency, currencyDisplay: 'narrowSymbol' })
        .formatToParts(0)
        .find((part) => part.type === 'currency')?.value ?? ''
    )
  } catch {
    return ''
  }
}

function DropLine({ reading, target }: { reading: PageReading | null; target: number | null }) {
  if (!reading) return <>&nbsp;</>
  if (reading.price === null) {
    return <>The page doesn’t publish its price. Type the most you want to pay; PriceWatch reads the price when it adds it.</>
  }
  if (target === null) return <>Type the most you want to pay.</>
  const drop = reading.price - target
  if (drop <= 0) {
    return (
      <>
        <span className="label-gap label-gap-under">At your price</span> Today’s price is already at or below this.
      </>
    )
  }
  return (
    <>
      <strong>{formatMoney(drop, reading.currency)}</strong> below today’s {formatMoney(reading.price, reading.currency)}
    </>
  )
}

function ProductImage({ reading }: { reading: PageReading }) {
  const [failed, setFailed] = useState(false)
  const host = hostOf(reading.url)
  if (reading.imageUrl && !failed) {
    return (
      <div className="bay-tile">
        <img className="bay-product" src={reading.imageUrl} alt="" onError={() => setFailed(true)} />
      </div>
    )
  }
  // no photo: the shop's name printed like a fascia strip, as on the shelf
  return (
    <span className="bay-product-blank" style={{ '--chars': host.length } as CSSProperties} aria-hidden="true">
      {host}
    </span>
  )
}

/**
 * Your price for a product on the shelf, measured against `price`, the one the label above prints. When the
 * bay carries the markdown sticker, the sticker says how far under it is and the desk doesn't repeat it.
 */
function OnShelf({
  product,
  price,
  stickered,
  justAdded,
}: {
  product: Product
  price: number | null
  stickered: boolean
  justAdded: boolean
}) {
  const { enabled: accounts } = useAccount()
  const gap = price === null ? null : price - product.targetPrice
  return (
    <div className="desk desk-on-shelf">
      <p className="desk-added" role="status">
        {justAdded ? (
          <>
            <Icon name="check" size={16} /> Added to your shelf
          </>
        ) : (
          <>Already on your shelf</>
        )}
      </p>
      <div className="desk-target">
        <span className="desk-caption">Your price</span>
        <span className="desk-target-row">
          <LabelPrice value={product.targetPrice} currency={product.currency} className="desk-target-price" />
          {gap !== null &&
            !stickered &&
            (gap <= 0 ? (
              <span className="label-gap label-gap-under">{gap === 0 ? 'Right on target' : `${formatMoney(-gap, product.currency)} under`}</span>
            ) : (
              <span className="label-gap">{formatMoney(gap, product.currency)} to go</span>
            ))}
        </span>
      </div>

      {!product.tracked && (
        <div className="notice notice-guest" role="note">
          <Icon name="tag" size={16} />
          {accounts ? (
            <>
              <span className="notice-title">Read once, not tracked yet</span>
              <a className="btn btn-ink btn-small notice-action" href={`${APP_URL}/`} target="_blank" rel="noreferrer">
                Sign up to keep tracking
              </a>
            </>
          ) : (
            <span>Read once. Tracking needs accounts, which are not set up on this PriceWatch.</span>
          )}
        </div>
      )}

      <a className="btn btn-ink btn-large desk-action" href={`${APP_URL}/`} target="_blank" rel="noreferrer">
        Open your shelf
        <Icon name="external" size={16} />
      </a>
    </div>
  )
}

function ProblemNotice({ problem, onRetry }: { problem: Problem; onRetry: () => void }) {
  return (
    <div className="notice notice-void desk-notice" role="alert">
      <Icon name={problem.icon} size={16} />
      <div className="notice-body">
        <p className="notice-title">{problem.title}</p>
        <p>{problem.detail}</p>
        {problem.next && <p className="notice-next">{problem.next}</p>}
        {problem.action === 'retry' && (
          <button type="button" className="btn btn-ink btn-small desk-notice-action" onClick={onRetry}>
            <Icon name="refresh" size={14} /> Try again
          </button>
        )}
        {problem.action === 'signIn' && (
          <a className="btn btn-ink btn-small desk-notice-action" href={`${APP_URL}/`} target="_blank" rel="noreferrer">
            Sign in on PriceWatch
          </a>
        )}
      </div>
    </div>
  )
}

function AccountSlot() {
  const account = useAccount()
  if (!account.enabled || account.status === 'loading') {
    return null
  }
  if (account.status === 'guest') {
    return (
      <a className="btn btn-small account-sign-in" href={`${APP_URL}/`} target="_blank" rel="noreferrer">
        Sign in
      </a>
    )
  }
  return (
    <a
      className="popup-avatar"
      href={`${APP_URL}/`}
      target="_blank"
      rel="noreferrer"
      title={account.email ? `Signed in as ${account.email}` : 'Signed in'}
    >
      {account.avatarUrl ? <img src={account.avatarUrl} alt="" /> : <Icon name="check" size={16} />}
      <span className="sr-only">{account.email ? `Signed in as ${account.email}. Open your shelf` : 'Open your shelf'}</span>
    </a>
  )
}
