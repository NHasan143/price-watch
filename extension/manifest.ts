/**
 * The extension's manifest, written into dist/ at build time so the PriceWatch addresses and the Clerk
 * key come from the environment (see .env.example).
 */

/**
 * Public half of the key that pins the extension ID while it is loaded unpacked, so the origin the
 * backend and Clerk allow is the same on every machine: chrome-extension://nkcpkjhamckjiabjibfdpaopfmkjnacd.
 * The Chrome Web Store assigns its own ID; see README, Chrome extension.
 */
const DEV_KEY =
  'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyp1bHqABCP/3DadLhiArLhdLjhu7YacsRNFJ6uEYRTXllmruBN8+5HLly5OgGWIvmG0Ejbv1w/sALLBzLPeI4QW33MhL4Fyj4Whc4Bhu/ZzvDOssGwFdW1Bra3F4RjEIvi3Se/d0Ba4PzrDNekEPdQerl8n2q4LeTQYh2y9i749PAMXPSrXwe3A+TQjzhV/we3UKIwqezu7SRIeXQAlahAwLaS51dXASPrX80mIruXp6H+JugsqCvbvWJFOWsVsLSOZbOH2tDOuSIFyXcnSgWPI11UPisSfXlDYWwb5h4+LIDJWaoI3k/GkiyoGeJbuUwVaoBbnjbo16gzys/yPbcQIDAQAB'

export interface ManifestOptions {
  version: string
  appUrl: string
  clerkPublishableKey: string | undefined
}

/** A match pattern for every page on the URL's host (patterns cannot name a port; they match all ports). */
function hostPattern(url: string): string {
  const { protocol, hostname } = new URL(url)
  return `${protocol}//${hostname}/*`
}

/** Clerk's Frontend API host is encoded in the publishable key: pk_test_<base64("host$")>. */
function clerkFrontendApi(publishableKey: string): string {
  const encoded = publishableKey.replace(/^pk_(test|live)_/, '')
  return Buffer.from(encoded, 'base64').toString('utf8').replace(/\$$/, '')
}

export function manifest({ version, appUrl, clerkPublishableKey }: ManifestOptions) {
  const icons = { 16: 'icons/icon-16.png', 32: 'icons/icon-32.png', 48: 'icons/icon-48.png', 128: 'icons/icon-128.png' }
  const app = hostPattern(appUrl)
  return {
    manifest_version: 3,
    name: 'PriceWatch',
    version,
    description: 'See the price of the product you are looking at and add it to your PriceWatch shelf in one click.',
    key: DEV_KEY,
    icons,
    action: { default_title: 'Watch this price', default_popup: 'popup.html', default_icon: icons },
    // activeTab: the page is read only when the shopper clicks the icon, so no "all websites" access.
    permissions: ['activeTab', 'scripting', 'storage', ...(clerkPublishableKey ? ['cookies'] : [])],
    // Clerk reads the web app's session (sync host) and talks to its Frontend API.
    host_permissions: clerkPublishableKey ? [app, `https://${clerkFrontendApi(clerkPublishableKey)}/*`] : [],
    content_scripts: [
      // Shares the guest id with the web app, so products added here land on the same shelf.
      { matches: [app], js: ['guest-sync.js'], run_at: 'document_start' },
    ],
  }
}
