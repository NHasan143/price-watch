import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import type { Plugin } from 'vite'
import { manifest } from './manifest'
import pkg from './package.json' with { type: 'json' }

const webSrc = fileURLToPath(new URL('../frontend/src', import.meta.url))

/** Writes manifest.json next to the built popup. */
function writeManifest(options: Parameters<typeof manifest>[0]): Plugin {
  return {
    name: 'pricewatch-manifest',
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'manifest.json', source: JSON.stringify(manifest(options), null, 2) })
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // One Clerk key for the app and the extension: fall back to the frontend's when the extension sets none.
  const frontendEnv = loadEnv(mode, fileURLToPath(new URL('../frontend', import.meta.url)), 'VITE_CLERK_')
  const env = { ...frontendEnv, ...loadEnv(mode, process.cwd(), 'VITE_') }
  const apiUrl = env.VITE_API_URL || 'http://localhost:8080'
  const appUrl = env.VITE_APP_URL || 'http://localhost:5173'
  const clerkPublishableKey = env.VITE_CLERK_PUBLISHABLE_KEY || undefined

  return {
    plugins: [react(), writeManifest({ version: pkg.version, appUrl, clerkPublishableKey })],
    define: {
      'import.meta.env.VITE_API_URL': JSON.stringify(apiUrl),
      'import.meta.env.VITE_APP_URL': JSON.stringify(appUrl),
      'import.meta.env.VITE_CLERK_PUBLISHABLE_KEY': JSON.stringify(clerkPublishableKey ?? ''),
    },
    resolve: {
      alias: { '@web': webSrc },
      // the shared web app files must use this package's React
      dedupe: ['react', 'react-dom'],
    },
    build: {
      // Extension pages may not load remote code; keep everything in the package.
      assetsInlineLimit: 0,
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        input: {
          popup: fileURLToPath(new URL('popup.html', import.meta.url)),
          'guest-sync': fileURLToPath(new URL('src/guest-sync.ts', import.meta.url)),
        },
        output: {
          // The content script is listed by name in the manifest and must be one plain script.
          entryFileNames: (chunk) => (chunk.name === 'guest-sync' ? 'guest-sync.js' : 'assets/[name]-[hash].js'),
        },
      },
    },
  }
})
