# PriceWatch – Frontend

React 19 + TypeScript single-page app for [PriceWatch](../README.md), built with Vite.

## Scripts

Run these from this `frontend/` folder.

| Command           | What it does                                                        |
| ----------------- | ------------------------------------------------------------------- |
| `npm install`     | Installs dependencies                                               |
| `npm run dev`     | Starts the dev server at <http://localhost:5173> with hot reload    |
| `npm run build`   | Type-checks (`tsc -b`) and creates a production build in `dist/`    |
| `npm run preview` | Serves the production build locally                                 |
| `npm run lint`    | Lints the code with [Oxlint](https://oxc.rs/docs/guide/usage/linter) |

Requires Node.js `^20.19` or `>=22.12` (a Vite 8 requirement).

## How it talks to the backend

During development the Vite dev server proxies every `/api/*` request to the Spring Boot
backend on <http://localhost:8080> (see `vite.config.ts`). The browser only ever talks to
`localhost:5173`, so no CORS setup is needed.

To host the frontend separately from the API, set the API address at build time:

```bash
VITE_API_BASE_URL=https://api.example.com npm run build
```

and add the frontend's origin to `pricewatch.cors.allowed-origins` in the backend.

## Source layout

```
src/
├── api/          # typed fetch client + TypeScript types matching the REST API
├── components/   # AddProductForm, ProductCard, PriceChart, StatusBadge
├── hooks/        # useProducts – loads the list and polls every 60 s
├── utils/        # money / date formatting helpers
├── App.tsx       # page layout and summary header
└── index.css     # all styles (CSS variables, light + dark theme)
```

The price chart (Recharts) is loaded lazily, so it is only downloaded the first time a
history panel is opened.
