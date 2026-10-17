# Changelog

All notable changes to the PriceWatch Chrome extension. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). Each version is released by pushing the tag `extension-v<version>`.

## [0.1.0] - 2026-10-18

First test build.

### Added

- Click the toolbar icon on a product page to see its name, photo and price, read the same way PriceWatch
  reads shops: structured data (JSON-LD, microdata, meta tags), Amazon's price block, then the price shown
  after the title.
- A suggested target 10% below today's price, with 5, 10, 15 and 20% presets, and one click to add the
  product to your shelf.
- Products already on your shelf show your saved target instead.
- Shares your guest shelf with the PriceWatch web app.
- Clear messages for pages without a price, pages Chrome does not let extensions read, shops that block
  price checks, the guest product limit, and PriceWatch not running.

[0.1.0]: https://github.com/NHasan143/price-watch/releases/tag/extension-v0.1.0
