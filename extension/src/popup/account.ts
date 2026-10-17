import { createContext, useContext } from 'react'
import type { TokenSource } from '../api'

export interface Account {
  /** Accounts are configured on this PriceWatch (a Clerk publishable key was built in). */
  enabled: boolean
  /** "loading" until Clerk has read the web app's session. */
  status: 'loading' | 'guest' | 'signedIn'
  token: TokenSource
  avatarUrl?: string
  email?: string
}

export const noToken: TokenSource = async () => null
export const GUEST: Account = { enabled: false, status: 'guest', token: noToken }

export const AccountContext = createContext<Account>(GUEST)

export function useAccount(): Account {
  return useContext(AccountContext)
}
