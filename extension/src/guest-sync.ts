/**
 * Content script on the PriceWatch web app, run before the app's own scripts. The web app keeps a
 * guest's id in localStorage and the extension keeps one in chrome.storage; this makes them the same
 * id, so a guest's products land on one shelf whichever side added them. The web app's id wins.
 *
 * Built as one plain script (no imports): content scripts cannot be modules.
 */
const KEY = 'pricewatch.guestId'

try {
  const appId = window.localStorage.getItem(KEY)
  if (appId) {
    void chrome.storage.local.set({ [KEY]: appId })
  } else {
    // The web app has no guest id yet: hand it the one the extension already used.
    void chrome.storage.local.get(KEY).then((stored) => {
      const id = stored[KEY]
      if (typeof id === 'string' && id && !window.localStorage.getItem(KEY)) {
        window.localStorage.setItem(KEY, id)
      }
    })
  }
} catch {
  // storage blocked on this page: each side keeps its own guest
}
