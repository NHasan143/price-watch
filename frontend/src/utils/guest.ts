const KEY = 'pricewatch.guestId'
let memoryId: string | null = null

/**
 * The random id this browser uses as a guest. The backend keeps a guest's products under it until
 * they sign up; it is the guest's only credential, so it never leaves this browser except to the API.
 */
export function guestId(): string {
  try {
    const stored = window.localStorage.getItem(KEY)
    if (stored) return stored
    const created = crypto.randomUUID()
    window.localStorage.setItem(KEY, created)
    return created
  } catch {
    // storage blocked (private window): a guest id for this page view only
    memoryId ??= crypto.randomUUID()
    return memoryId
  }
}

/** After signing up the guest's products belong to the account; the next guest visit starts fresh. */
export function forgetGuestId(): void {
  memoryId = null
  try {
    window.localStorage.removeItem(KEY)
  } catch {
    // nothing stored
  }
}
