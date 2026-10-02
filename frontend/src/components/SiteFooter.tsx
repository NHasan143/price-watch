export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer-inner">
        <p>Prices are read from public product pages. Check a store’s terms before tracking it.</p>
        <p className="credit">
          Created with
          <svg className="credit-heart" viewBox="0 0 24 24" width="16" height="16" role="img" aria-label="love">
            <path d="M12 20.5s-7.5-4.6-9.2-9.4C1.6 7.6 3.8 4.5 7.1 4.5c2 0 3.6 1.1 4.9 2.9 1.3-1.8 2.9-2.9 4.9-2.9 3.3 0 5.5 3.1 4.3 6.6-1.7 4.8-9.2 9.4-9.2 9.4z" />
          </svg>
          by{' '}
          <a href="https://www.linkedin.com/in/naymulhasan143/" target="_blank" rel="noopener noreferrer">
            Naymul Hasan<span className="sr-only"> (LinkedIn, opens in a new tab)</span>
          </a>
        </p>
      </div>
    </footer>
  )
}
