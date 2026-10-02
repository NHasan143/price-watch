# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Shoppers in general who want to buy a specific product but not at today's price. They paste a product link,
say what they are willing to pay, and come back (or get an email) when the price reaches it. They are not
analysts: they check in occasionally, want the answer "is it cheap enough yet?" at a glance, and track a
handful to a few dozen products at a time.

## Product Purpose

PriceWatch watches public product pages and records every price it reads, so the shopper does not have to
refresh shops by hand. Success is a shopper buying at or below their target price because PriceWatch told them
the moment it happened, and trusting the number because they can see the history behind it.

## Positioning

It works on any shop with a link, not a fixed list of partner stores: it reads the price the shop already
publishes for search engines (JSON-LD, microdata, meta tags), and falls back to a user-supplied CSS selector.
It is self-hosted and keeps the full price history locally.

## Operating Context

- Add a product: paste a URL, set a target ("alert me at or below"), optional name, optional CSS selector.
- Prices are re-checked on a schedule (every 12 h by default) and on demand ("Check now").
- Alerts fire once when a price crosses the target and re-arm after it rises again; they are logged and, if SMTP
  is configured, emailed.
- Shops are global: prices arrive in mixed currencies (USD, EUR, BDT, GBP …) side by side.

## Capabilities and Constraints

- Frontend: React 19 + TypeScript + Vite; talks to the Spring Boot REST API under `/api/products`. The API is
  fixed for UI work.
- Per product: name, URL, image, currency, target, current, lowest, highest, below-target flag, last checked,
  last error, created date, full price history (chart and table).
- Actions: create, check now, edit target, delete, view history.
- A check can fail (shop blocks the request, price rendered by JavaScript); the last known price is kept and the
  error shown.
- Guest first: anyone can add a product without an account and see its price once (read once, not tracked).
- Accounts (sign in / sign up via Clerk): signing up keeps products tracked (checked twice a day) with email alerts
  to the account; a guest's products move to the account on sign-up.
- Data: products, price history and the accounts table are stored by PriceWatch on the owner's machine for now
  (later their hosting); Clerk stores sign-in credentials.

## Evidence on Hand

No testimonials, users, benchmarks or press exist. Do not fabricate savings totals, user counts or store
partnerships.

## Product Principles

1. The answer first: is each product at or below target, and by how much.
2. Trust through history: every number can be traced to recorded checks.
3. Honest failure: when a shop cannot be read, say so plainly and keep the last good data.
4. Currency-neutral: never assume one currency or locale.

## Accessibility & Inclusion

Keyboard operable, visible focus, charts have a text/table alternative, light and dark themes, respects
reduced motion.
