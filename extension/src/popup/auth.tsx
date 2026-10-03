import { useMemo } from 'react'
import type { ReactNode } from 'react'
import { ClerkFailed, ClerkLoaded, ClerkLoading, ClerkProvider, useAuth, useUser } from '@clerk/chrome-extension'
import { APP_URL, CLERK_PUBLISHABLE_KEY } from '../config'
import { AccountContext, GUEST, noToken } from './account'
import type { Account } from './account'

/**
 * Signed in on the PriceWatch web app means signed in here: Clerk's sync host reads the web app's
 * session, so the popup never shows a sign-in form of its own.
 */
export function AccountProvider({ children }: { children: ReactNode }) {
  if (!CLERK_PUBLISHABLE_KEY) {
    return <AccountContext.Provider value={GUEST}>{children}</AccountContext.Provider>
  }
  return (
    <ClerkProvider publishableKey={CLERK_PUBLISHABLE_KEY} syncHost={APP_URL}>
      <ClerkLoading>
        <AccountContext.Provider value={{ enabled: true, status: 'loading', token: noToken }}>{children}</AccountContext.Provider>
      </ClerkLoading>
      <ClerkLoaded>
        <Session>{children}</Session>
      </ClerkLoaded>
      <ClerkFailed>
        {/* Clerk could not load (offline, extension origin not allowed): carry on as a guest. */}
        <AccountContext.Provider value={{ enabled: true, status: 'guest', token: noToken }}>{children}</AccountContext.Provider>
      </ClerkFailed>
    </ClerkProvider>
  )
}

function Session({ children }: { children: ReactNode }) {
  const { isSignedIn, getToken } = useAuth()
  const { user } = useUser()
  const value = useMemo<Account>(
    () =>
      isSignedIn
        ? {
            enabled: true,
            status: 'signedIn',
            token: () => getToken(),
            avatarUrl: user?.imageUrl,
            email: user?.primaryEmailAddress?.emailAddress,
          }
        : { enabled: true, status: 'guest', token: noToken },
    [isSignedIn, getToken, user],
  )
  return <AccountContext.Provider value={value}>{children}</AccountContext.Provider>
}
