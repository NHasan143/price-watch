/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Where the PriceWatch API runs, for example http://localhost:8080. */
  readonly VITE_API_URL: string
  /** Where the PriceWatch web app runs, for example http://localhost:5173. */
  readonly VITE_APP_URL: string
  /** Clerk publishable key; empty when accounts are not configured. */
  readonly VITE_CLERK_PUBLISHABLE_KEY: string
}
