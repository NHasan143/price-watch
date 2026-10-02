import { useEffect, useState } from 'react'
import { ClerkFailed, ClerkLoaded, ClerkLoading, ClerkProvider, getToken, useAuth } from '@clerk/react'
import App from './App'
import { api } from './api/client'
import { AccountsContext, setTokenProvider } from './auth/accounts'
import { clerkTheme } from './auth/appearance'
import { useMediaQuery } from './auth/usePreference'
import { forgetGuestId } from './utils/guest'

const publishableKey: string | undefined = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

const GUESTS_ONLY = { enabled: false, signedIn: false }

/** Everyone can use the shelf; signing in (Clerk) keeps products tracked and sends alerts. */
export function Accounts() {
  const dark = useMediaQuery('(prefers-color-scheme: dark)')
  if (!publishableKey) {
    // Accounts are not configured: the app still works, as a guest.
    return (
      <AccountsContext.Provider value={GUESTS_ONLY}>
        <App />
      </AccountsContext.Provider>
    )
  }
  return (
    <ClerkProvider publishableKey={publishableKey} appearance={clerkTheme(dark)} afterSignOutUrl="/">
      <ClerkLoading>
        <AccountsContext.Provider value={{ enabled: true, signedIn: false }}>
          <App waiting />
        </AccountsContext.Provider>
      </ClerkLoading>
      <ClerkLoaded>
        <Session />
      </ClerkLoaded>
      <ClerkFailed>
        {/* Clerk could not load (offline, blocked): carry on as a guest rather than show nothing. */}
        <AccountsContext.Provider value={GUESTS_ONLY}>
          <App />
        </AccountsContext.Provider>
      </ClerkFailed>
    </ClerkProvider>
  )
}

function Session() {
  const { isSignedIn, userId } = useAuth()
  // Signed in: wait until any guest products have moved to the account before loading the shelf.
  const [claimedFor, setClaimedFor] = useState<string | null>(null)

  useEffect(() => {
    setTokenProvider(isSignedIn ? () => getToken() : null)
    if (!isSignedIn || !userId) {
      return
    }
    let cancelled = false
    api
      .claimGuestProducts()
      .then(() => forgetGuestId())
      .catch(() => {
        // nothing to claim, or a hiccup: the guest products stay claimable on the next sign-in
      })
      .finally(() => {
        if (!cancelled) setClaimedFor(userId)
      })
    return () => {
      cancelled = true
    }
  }, [isSignedIn, userId])

  const signedIn = Boolean(isSignedIn)
  const ready = !signedIn || claimedFor === userId
  return (
    <AccountsContext.Provider value={{ enabled: true, signedIn }}>
      {/* A different account (or signing out) gets a fresh shelf. */}
      <App key={userId ?? 'guest'} waiting={!ready} />
    </AccountsContext.Provider>
  )
}
