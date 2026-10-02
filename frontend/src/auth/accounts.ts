import { createContext, useContext } from 'react'

export interface AccountsState {
  /** Accounts are configured (a Clerk publishable key is set). Without them everyone is a guest. */
  enabled: boolean
  signedIn: boolean
}

export const AccountsContext = createContext<AccountsState>({ enabled: false, signedIn: false })

export function useAccounts(): AccountsState {
  return useContext(AccountsContext)
}

/** Supplies the Clerk session token for API calls while someone is signed in; null for guests. */
let tokenProvider: (() => Promise<string | null>) | null = null

export function setTokenProvider(provider: (() => Promise<string | null>) | null): void {
  tokenProvider = provider
}

export async function sessionToken(): Promise<string | null> {
  return tokenProvider ? tokenProvider().catch(() => null) : null
}
