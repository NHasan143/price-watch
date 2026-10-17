import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource-variable/archivo/wdth.css'
// The web app's stylesheet: one source for the tokens, labels, rails, buttons and notices.
import '@web/index.css'
import './popup.css'
import { AccountProvider } from './auth'
import { Popup } from './Popup'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AccountProvider>
      <Popup />
    </AccountProvider>
  </StrictMode>,
)
