import type { IconName } from '../components/Icon'
import type { Product } from '../api/types'
import { hostOf, timeAgo } from './format'

const LAST_PRICE = 'The price shown is the last one we read.'

/** After this many blocked checks in a row, say the shop may not be trackable at all. */
const BLOCKED_STREAK = 3

export interface FailureCopy {
  icon: IconName
  title: string
  next: string
}

function retryLine(nextRetryAt: string | null, now: number): string {
  if (!nextRetryAt) {
    return 'Next try at the regular check.'
  }
  return Date.parse(nextRetryAt) - now < 60_000 ? 'Trying again in a moment.' : `Trying again ${timeAgo(nextRetryAt)}.`
}

/** Headline and next step for a product whose last check failed, by the reason the backend gave. */
export function failureCopy(product: Product, now = Date.now()): FailureCopy {
  const host = hostOf(product.url)
  switch (product.lastErrorReason) {
    case 'BLOCKED':
      return {
        icon: 'blocked',
        title: `${host} blocks automated checks`,
        next:
          product.failedChecks >= BLOCKED_STREAK
            ? `Blocked on the last ${product.failedChecks} checks, so this shop may not be trackable automatically. ${LAST_PRICE}`
            : `PriceWatch won’t ask again early. Next try at the regular check. ${LAST_PRICE}`,
      }
    case 'TEMPORARY':
      return {
        icon: 'clock',
        title: `Couldn’t reach ${host}`,
        next: `${retryLine(product.nextRetryAt, now)} ${LAST_PRICE}`,
      }
    case 'PAGE_GONE':
      return {
        icon: 'alert',
        title: 'The product page is gone',
        next: `Open the link to check, or delete this label. ${LAST_PRICE}`,
      }
    case 'NO_PRICE':
      return { icon: 'alert', title: 'No price on the page', next: LAST_PRICE }
    case 'INVALID':
      return { icon: 'alert', title: 'This link can’t be read', next: LAST_PRICE }
    default:
      return { icon: 'alert', title: 'Last check failed', next: LAST_PRICE }
  }
}
