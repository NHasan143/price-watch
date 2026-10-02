import { SignInButton, SignUpButton, UserButton } from '@clerk/react'
import { useAccounts } from '../auth/accounts'

/**
 * Right end of the aisle band. Guests: "Sign in" and "Sign up", which open Clerk's own card over the
 * shelf. Signed in: the account avatar and Clerk's menu. Nothing when accounts are not configured.
 */
export function AccountControls() {
  const { enabled, signedIn } = useAccounts()
  if (!enabled) {
    return null
  }
  if (signedIn) {
    return (
      <div className="account">
        <UserButton />
      </div>
    )
  }
  return (
    <div className="account account-guest">
      <SignInButton mode="modal">
        <button type="button" className="btn account-sign-in">
          Sign in
        </button>
      </SignInButton>
      <SignUpButton mode="modal">
        <button type="button" className="btn account-sign-up">
          Sign up
        </button>
      </SignUpButton>
    </div>
  )
}
