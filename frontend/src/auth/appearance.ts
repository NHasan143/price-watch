import type { ClerkProviderProps } from '@clerk/react'

type Appearance = NonNullable<ClerkProviderProps['appearance']>

/**
 * Clerk's own sign-in / sign-up card, unchanged except for the theme chosen in Clerk's theme editor
 * (light and dark variables). The card is Clerk's design, not PriceWatch's label style.
 */
export function clerkTheme(dark: boolean): Appearance {
  const shared = { colorDanger: '#EF4444', colorSuccess: '#22C543', colorWarning: '#F36B16', colorShimmer: '#ffffff', colorModalBackdrop: '#000000' }
  return {
    variables: dark
      ? {
          ...shared,
          colorBackground: '#212126',
          colorNeutral: 'white',
          colorPrimary: '#FFFFFF',
          colorPrimaryForeground: 'black',
          colorForeground: 'white',
          colorInputForeground: 'white',
          colorInput: '#26262B',
          colorRing: 'color-mix(in srgb, #ffffff 15%, transparent)',
        }
      : {
          ...shared,
          colorPrimary: '#2F3037',
          colorPrimaryForeground: '#ffffff',
          colorNeutral: '#000000',
          colorForeground: '#000000',
          colorBackground: '#ffffff',
          colorInput: '#ffffff',
          colorInputForeground: '#000000',
          colorRing: 'color-mix(in srgb, #000000 15%, transparent)',
        },
  }
}
