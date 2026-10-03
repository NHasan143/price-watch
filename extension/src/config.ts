/** Set at build time from extension/.env.local (see .env.example). */
export const API_URL: string = import.meta.env.VITE_API_URL.replace(/\/$/, '')
export const APP_URL: string = import.meta.env.VITE_APP_URL.replace(/\/$/, '')
export const CLERK_PUBLISHABLE_KEY: string | null = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY || null

