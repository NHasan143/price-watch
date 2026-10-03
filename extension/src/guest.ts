/** Same key the web app uses in localStorage (frontend/src/utils/guest.ts); guest-sync.ts keeps the two equal. */
export const GUEST_KEY = 'pricewatch.guestId'

/** The guest id shared with the web app, created here when the shopper has not opened the web app yet. */
export async function guestId(): Promise<string> {
  const stored = (await chrome.storage.local.get(GUEST_KEY))[GUEST_KEY]
  if (typeof stored === 'string' && stored) {
    return stored
  }
  const created = crypto.randomUUID()
  await chrome.storage.local.set({ [GUEST_KEY]: created })
  return created
}
