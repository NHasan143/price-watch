/** What the popup read from the shop's page. */
export interface PageReading {
  /** The page's canonical address when it names one, so tracking parameters don't create a second label. */
  url: string
  name: string | null
  imageUrl: string | null
  /** Null when no price could be found on the page. */
  price: number | null
  /** ISO 4217 code, when the page names one. */
  currency: string | null
}

/**
 * Reads the product from the open page the way the backend does (backend/.../scraper): the price from
 * schema.org JSON-LD, then microdata (itemprop="price"), then the product/Open Graph meta tags, then the
 * price shown after the product title (VisiblePriceExtractor); the name like ProductNameFinder; numbers
 * like PriceParser. Reading the live page also sees prices that shops draw with JavaScript.
 *
 * Runs inside the shop's page via chrome.scripting.executeScript, which sends the function's source,
 * so it must not use anything from outside its own body.
 */
export function readProductPage(): PageReading {
  type Json = Record<string, unknown>

  const text = (value: unknown): string | null => {
    if (typeof value !== 'string') return null
    const trimmed = value.replace(/\s+/g, ' ').trim()
    return trimmed || null
  }

  // ---- numbers (PriceParser) ----
  /**
   * The first number in the text as a price. A single separator followed by exactly three digits
   * ("1,234", "1.234") groups thousands; anything else is the decimal separator ("28500.0000", "12,5").
   * When both "." and "," appear, the last one is the decimal separator.
   */
  const amount = (value: unknown): number | null => {
    if (typeof value === 'number') return Number.isFinite(value) && value > 0 ? Math.round(value * 100) / 100 : null
    if (typeof value !== 'string') return null
    const match = /\d(?:[\d.,   ]*\d)?/.exec(value)
    if (!match) return null
    const number = match[0].replace(/[   ]/g, '')
    const separatorIndex = Math.max(number.lastIndexOf('.'), number.lastIndexOf(','))
    let normalized = number
    if (separatorIndex >= 0) {
      const separator = number[separatorIndex]
      const other = separator === '.' ? ',' : '.'
      const integerPart = number.slice(0, separatorIndex)
      const decimal =
        number.includes(other) ||
        integerPart === '0' ||
        (number.indexOf(separator) === separatorIndex && number.length - separatorIndex - 1 !== 3)
      normalized = decimal
        ? `${integerPart.replace(/[.,]/g, '')}.${number.slice(separatorIndex + 1)}`
        : number.replace(/[.,]/g, '')
    }
    const parsed = Math.round(Number(normalized) * 100) / 100
    return Number.isFinite(parsed) && parsed > 0 && parsed <= 9999999999.99 ? parsed : null
  }

  const currencyCode = (value: unknown): string | null => {
    const code = text(value)?.toUpperCase() ?? null
    if (!code || !/^[A-Z]{3}$/.test(code)) return null
    try {
      new Intl.NumberFormat('en', { style: 'currency', currency: code })
      return code
    } catch {
      return null
    }
  }

  const SYMBOLS: [string, string][] = [
    ['€', 'EUR'], ['£', 'GBP'], ['₹', 'INR'], ['৳', 'BDT'], ['₩', 'KRW'],
    ['₺', 'TRY'], ['₽', 'RUB'], ['₫', 'VND'], ['¥', 'JPY'], ['$', 'USD'],
  ]
  /** An ISO code ("USD") or a well-known symbol ("$") in the text; "Tk" is always the taka. */
  const detectCurrency = (raw: string): string | null => {
    const withTaka = raw.replace(/\bTk\.?/gi, 'BDT')
    for (const match of withTaka.matchAll(/(?<![A-Za-z])[A-Z]{3}(?![A-Za-z])/g)) {
      const code = currencyCode(match[0])
      if (code) return code
    }
    return SYMBOLS.find(([symbol]) => withTaka.includes(symbol))?.[1] ?? null
  }

  const list = (value: unknown): unknown[] => (Array.isArray(value) ? value : value == null ? [] : [value])

  const absolute = (value: unknown): string | null => {
    const raw = text(value)
    if (!raw) return null
    try {
      const url = new URL(raw, document.baseURI)
      return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
    } catch {
      return null
    }
  }

  const meta = (...names: string[]): string | null => {
    for (const name of names) {
      const element = document.querySelector(`meta[property="${name}"], meta[name="${name}"]`)
      const content = text(element?.getAttribute('content'))
      if (content) return content
    }
    return null
  }

  // ---- schema.org JSON-LD ----
  const products: Json[] = []
  const visit = (node: unknown, depth: number): void => {
    if (depth > 8) return
    for (const item of list(node)) {
      if (!item || typeof item !== 'object') continue
      const object = item as Json
      const types = list(object['@type']).map((type) => String(type).replace(/^.*[/#]/, ''))
      if (types.some((type) => type.includes('Product') || type === 'Book' || type === 'Vehicle')) {
        products.push(object)
      }
      visit(object['@graph'], depth + 1)
      visit(object.mainEntity, depth + 1)
    }
  }
  for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
    try {
      visit(JSON.parse(script.textContent ?? ''), 0)
    } catch {
      // a shop's malformed block: skip it
    }
  }

  const offerPrice = (offers: unknown): { price: number; currency: string | null } | null => {
    for (const item of list(offers)) {
      if (!item || typeof item !== 'object') continue
      const offer = item as Json
      const specs = list(offer.priceSpecification) as Json[]
      const price =
        amount(offer.price) ?? amount(offer.lowPrice) ?? specs.map((spec) => amount(spec?.price)).find((p) => p !== null) ?? null
      if (price !== null) {
        const currency =
          currencyCode(offer.priceCurrency) ?? specs.map((spec) => currencyCode(spec?.priceCurrency)).find(Boolean) ?? null
        return { price, currency }
      }
      const nested = offerPrice(offer.offers)
      if (nested) return nested
    }
    return null
  }

  const imageOf = (value: unknown): string | null => {
    for (const item of list(value)) {
      const url = typeof item === 'object' && item ? absolute((item as Json).url ?? (item as Json).contentUrl) : absolute(item)
      if (url) return url
    }
    return null
  }

  let price: number | null = null
  let currency: string | null = null
  let imageUrl: string | null = null
  for (const product of products) {
    const offer = offerPrice(product.offers) ?? offerPrice((list(product.hasVariant)[0] as Json | undefined)?.offers)
    imageUrl ??= imageOf(product.image)
    if (offer) {
      price = offer.price
      currency = offer.currency
      imageUrl = imageOf(product.image) ?? imageUrl
      break
    }
  }

  // ---- microdata ----
  if (price === null) {
    const element = document.querySelector('[itemprop="price"]')
    price = amount(element?.getAttribute('content') ?? element?.textContent)
    if (price !== null) {
      const currencyElement = document.querySelector('[itemprop="priceCurrency"]')
      currency = currencyCode(currencyElement?.getAttribute('content') ?? currencyElement?.textContent)
    }
  }

  // ---- product / Open Graph meta tags (sale price first) ----
  if (price === null) {
    price = amount(meta('product:sale_price:amount', 'product:price:amount', 'og:price:amount'))
    if (price !== null) {
      currency = currencyCode(meta('product:sale_price:currency', 'product:price:currency', 'og:price:currency'))
    }
  }

  // ---- Amazon: the price to pay, which it prints for screen readers inside the price block ----
  if (price === null) {
    const amazon = document.querySelector(
      '#corePriceDisplay_desktop_feature_div .priceToPay .a-offscreen, #corePrice_feature_div .a-price .a-offscreen, ' +
        '#corePrice_desktop .a-price .a-offscreen, #apex_desktop .a-price .a-offscreen, #priceblock_dealprice, #priceblock_ourprice',
    )
    const raw = text(amazon?.textContent)
    if (raw) {
      price = amount(raw)
      currency = price === null ? null : detectCurrency(raw)
    }
  }

  // ---- the first price shown after the product title (VisiblePriceExtractor) ----
  const title =
    document.querySelector('#productTitle') ??
    [...document.querySelectorAll('h1')].find((h1) => (h1 as HTMLElement).offsetParent !== null && text(h1.textContent)) ??
    null
  if (price === null && title) {
    const priceText =
      /(?:[€£₹৳₩₺₽₫¥$]|\b[A-Z]{3}\b|\b(?:Tk|Rs)\.?)\s*\d|\d[\d.,\s]*\s*(?:[€£₹৳₩₺₽₫¥$]|\b[A-Z]{3}\b)/
    const notCurrentText = /\b(?:emi|\/\s*mo(?:nth)?|per\s+month|monthly|save|saving|off|was|before)\b/i
    const notCurrentClass =
      /(?:^|[-_\s])(?:old|was|compare|strike|through|deleted|del|emi|instal\w*|monthly|saving|save)(?=$|[-_\s])/i
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT)
    walker.currentNode = title
    for (let seen = 0; seen < 150 && walker.nextNode(); seen++) {
      const element = walker.currentNode as HTMLElement
      const raw = text(element.textContent)
      if (!raw || raw.length > 40 || !priceText.test(raw) || notCurrentText.test(raw)) continue
      // the innermost element holding the price, so its own classes describe it
      if ([...element.children].some((child) => priceText.test(child.textContent ?? ''))) continue
      if (!detectCurrency(raw) && !/\bRs\.?/.test(raw)) continue
      let crossedOut = false
      for (let node: HTMLElement | null = element; node && node !== title.parentElement; node = node.parentElement) {
        const tag = node.tagName.toLowerCase()
        if (['del', 's', 'strike', 'button', 'script', 'style', 'option'].includes(tag)) crossedOut = true
        if (notCurrentClass.test(`${typeof node.className === 'string' ? node.className : ''} ${node.id}`)) crossedOut = true
        if (getComputedStyle(node).textDecorationLine.includes('line-through')) crossedOut = true
        if (crossedOut) break
      }
      if (crossedOut) continue
      const found = amount(raw)
      if (found !== null) {
        price = found
        currency = detectCurrency(raw)
        break
      }
    }
  }

  // ---- name (ProductNameFinder): the product's own name first, page titles last, then cleaned ----
  const host = location.hostname.toLowerCase().replace(/^www\./, '')
  const key = (value: string) => value.toLowerCase().replace(/[^\p{L}\p{N}]/gu, '')
  const siteKeys = new Set<string>([key(host)])
  const siteName = meta('og:site_name')
  if (siteName) siteKeys.add(key(siteName))
  const labels = host.split('.')
  for (const label of labels.slice(0, -1)) {
    if (!['www', 'm', 'com', 'co', 'net', 'org', 'shop', 'store'].includes(label)) siteKeys.add(key(label))
  }
  for (const k of [...siteKeys]) if (k.length < 3) siteKeys.delete(k)

  const microdataName = (): string | null => {
    for (const scope of document.querySelectorAll('[itemscope][itemtype]')) {
      if (!/schema\.org\/(Product|ProductGroup|ProductModel|IndividualProduct)\/?$/i.test(scope.getAttribute('itemtype')?.trim() ?? '')) {
        continue
      }
      for (const element of scope.querySelectorAll('[itemprop="name"]')) {
        if (element.parentElement?.closest('[itemscope]') !== scope) continue
        const value = text(element.getAttribute('content') ?? element.textContent)
        if (value) return value
      }
    }
    return null
  }

  const clean = (name: string): string => {
    const separator = /\s*\|\s*|\s+[-–—:]\s+|(?<=\S):\s+/g
    const parts: string[] = []
    const separators: string[] = []
    let start = 0
    for (const match of name.matchAll(separator)) {
      parts.push(name.slice(start, match.index))
      separators.push(match[0])
      start = match.index + match[0].length
    }
    parts.push(name.slice(start))
    let result = name
    if (parts.length > 1) {
      const isSite = (part: string) => key(part) !== '' && siteKeys.has(key(part))
      let siteSeparator: string | null = null
      if (isSite(parts[0])) {
        parts.shift()
        siteSeparator = separators.shift() ?? null
      } else if (isSite(parts[parts.length - 1])) {
        parts.pop()
        siteSeparator = separators.pop() ?? null
      }
      if (siteSeparator !== null && parts.length > 1) {
        const last = parts[parts.length - 1].trim()
        const lastSeparator = separators[separators.length - 1]
        const spaced = lastSeparator.includes('|') || /^\s/.test(lastSeparator)
        const category = last.length > 0 && last.length <= 40 && last.split(/\s+/).length <= 4 && !/\d/.test(last)
        const kept = parts.slice(0, -1).join('')
        if (spaced && lastSeparator.trim() === siteSeparator.trim() && category && kept.length > last.length) {
          parts.pop()
          separators.pop()
        }
      }
      result = parts.reduce((joined, part, i) => (i === 0 ? part : joined + separators[i - 1] + part), '')
    }
    const seoTails = result
      .replace(
        /(?:\s*[-|–—:,]\s*|\s+)(?:online\s+)?(?:at\s+)?(?:the\s+)?(?:(?:best|lowest)\s+)?prices?\s+in\s+(?:bangladesh|bd|india|pakistan|nepal|sri\s+lanka)(?:\s+\d{4})?$/i,
        '',
      )
      .replace(/(?:\s*[-|–—:,]\s*|\s+)(?:online\s+)?at\s+(?:the\s+)?(?:best|lowest)\s+prices?$/i, '')
    const buy = (seoTails === result ? /^buy\s+(.+?)\s+online$/i : /^buy\s+(.+?)(?:\s+online)?$/i).exec(seoTails)
    const cleaned = (buy ? buy[1] : seoTails).trim()
    return cleaned || name
  }

  const jsonLdName = products.map((product) => text(product.name)).find(Boolean) ?? null
  const rawName =
    jsonLdName ??
    microdataName() ??
    text(document.querySelector('#productTitle')?.textContent) ??
    text(meta('og:title')) ??
    text(document.title)
  const name = rawName ? clean(rawName).slice(0, 255) : null

  imageUrl ??=
    absolute(meta('og:image', 'twitter:image')) ??
    absolute(document.querySelector('#landingImage, #imgBlkFront')?.getAttribute('src'))

  // ---- address: the canonical link, unless it points somewhere unrelated (a shop's home page) ----
  const here = new URL(location.href)
  here.hash = ''
  for (const param of [...here.searchParams.keys()]) {
    if (/^(utm_|gclid$|fbclid$|msclkid$|mc_)/i.test(param)) here.searchParams.delete(param)
  }
  const canonical = absolute(document.querySelector('link[rel="canonical"]')?.getAttribute('href'))
  let url = here.href
  if (canonical) {
    const target = new URL(canonical)
    if (target.hostname.replace(/^www\./, '') === here.hostname.replace(/^www\./, '') && target.pathname !== '/') {
      url = target.href
    }
  }

  return { url, name, imageUrl, price, currency }
}
