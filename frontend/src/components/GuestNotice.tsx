import { SignUpButton } from '@clerk/react'
import { useAccounts } from '../auth/accounts'
import { Icon } from './Icon'

/** Under a guest's product: it was read once, and signing up is what keeps it on the shelf. */
export function GuestNotice() {
  const { enabled } = useAccounts()
  return (
    <div className="notice notice-guest" role="note">
      <Icon name="tag" size={16} />
      {enabled ? (
        <>
          <strong className="notice-title">Read once, not tracked yet</strong>
          <SignUpButton mode="modal">
            <button type="button" className="btn btn-ink btn-small notice-action">
              Sign up to keep tracking
            </button>
          </SignUpButton>
        </>
      ) : (
        <span>
          <strong className="notice-title">Read once.</strong> Tracking needs accounts, which are not set up on this
          PriceWatch.
        </span>
      )}
    </div>
  )
}
