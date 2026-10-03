---
name: PriceWatch
description: Shelf-edge price labels on graphite rails; the yellow markdown sticker lands when a price reaches your target.
colors:
  wall: "#e6e8e7"
  wall-line: "#cdd1cf"
  wall-hover: "#d9dcdb"
  bay: "#f4f5f4"
  rail: "#2a2e32"
  rail-hi: "#3c4147"
  text: "#141414"
  text-2: "#464a4f"
  fascia: "#c2c6c4"
  label: "#ffffff"
  ink: "#141414"
  ink-2: "#3f4247"
  ink-3: "#5f6368"
  hair: "#e3e4e2"
  hair-strong: "#b9bcbb"
  sticker: "#ffd21f"
  wobbler: "#c8232c"
  laser: "#ff2b2b"
  stamp: "#a8352f"
  stamp-soft: "#fcecea"
  wall-dark: "#131517"
  wall-line-dark: "#2a2e32"
  wall-hover-dark: "#22262a"
  bay-dark: "#1c1f22"
  rail-dark: "#07080a"
  rail-hi-dark: "#25292e"
  text-dark: "#eceeef"
  text-2-dark: "#a9aeb3"
  fascia-dark: "#3f454b"
  label-bone: "#ecebe4"
  hair-bone: "#d8d7cf"
  hair-strong-bone: "#aeada6"
  wobbler-dark: "#d6353b"
typography:
  display:
    fontFamily: "'Archivo Variable', 'Archivo', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.625rem"
    fontWeight: 850
    lineHeight: 1
    letterSpacing: "-0.02em"
    fontVariation: "'wdth' 125"
  headline:
    fontFamily: "'Archivo Variable', 'Archivo', system-ui, sans-serif"
    fontSize: "1.625rem"
    fontWeight: 850
    lineHeight: 1.1
    letterSpacing: "-0.02em"
    fontVariation: "'wdth' 88"
  price:
    fontFamily: "'Archivo Variable', 'Archivo', system-ui, sans-serif"
    fontSize: "clamp(2rem, 1.55rem + 1.2vw, 2.5rem)"
    fontWeight: 850
    lineHeight: 0.9
    letterSpacing: "-0.03em"
    fontFeature: "'tnum' 1, 'lnum' 1"
    fontVariation: "'wdth' 106"
  title:
    fontFamily: "'Archivo Variable', 'Archivo', system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 700
    lineHeight: 1.2
    fontVariation: "'wdth' 82"
  sign:
    fontFamily: "'Archivo Variable', 'Archivo', system-ui, sans-serif"
    fontSize: "1.1875rem"
    fontWeight: 900
    lineHeight: 1
    letterSpacing: "0.01em"
    fontVariation: "'wdth' 112"
  body:
    fontFamily: "'Archivo Variable', 'Archivo', system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
    fontFeature: "'tnum' 1"
  small:
    fontFamily: "'Archivo Variable', 'Archivo', system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "'Archivo Variable', 'Archivo', system-ui, sans-serif"
    fontSize: "0.6875rem"
    fontWeight: 700
    letterSpacing: "0.04em"
rounded:
  tag: "2px"
  control: "3px"
  panel: "4px"
  sticker: "7px"
  wobbler: "50%"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  xxl: "36px"
  section: "48px"
components:
  label:
    backgroundColor: "{colors.label}"
    textColor: "{colors.ink}"
    rounded: "{rounded.tag}"
    padding: "12px 14px 10px"
  label-dark:
    backgroundColor: "{colors.label-bone}"
    textColor: "{colors.ink}"
  rail:
    backgroundColor: "{colors.rail}"
    padding: "8px 14px 10px"
  label-void-tag:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.label}"
    rounded: "{rounded.tag}"
    padding: "1px 6px"
  label-stamp-tag:
    backgroundColor: "{colors.stamp-soft}"
    textColor: "{colors.stamp}"
    rounded: "{rounded.tag}"
    padding: "1px 6px"
  sticker:
    backgroundColor: "{colors.sticker}"
    textColor: "{colors.ink}"
    typography: "{typography.sign}"
    rounded: "{rounded.sticker}"
    padding: "8px 12px 9px"
  wobbler:
    backgroundColor: "{colors.wobbler}"
    textColor: "{colors.label}"
    rounded: "{rounded.wobbler}"
    size: "92px"
  readout:
    backgroundColor: "{colors.label}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "6px 12px 7px"
  readout-hit:
    backgroundColor: "{colors.sticker}"
    textColor: "{colors.ink}"
  button-ink:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.label}"
    rounded: "{rounded.control}"
    padding: "8px 12px"
  button-ink-hover:
    backgroundColor: "#000000"
  button-ink-large:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.label}"
    rounded: "{rounded.control}"
    padding: "13px 20px"
  button-ghost:
    textColor: "{colors.text-2}"
    rounded: "{rounded.control}"
    padding: "6px 8px"
  button-ghost-hover:
    backgroundColor: "{colors.wall-hover}"
    textColor: "{colors.text}"
  filter:
    textColor: "{colors.text-2}"
    rounded: "{rounded.control}"
    padding: "6px 10px"
  filter-active:
    backgroundColor: "{colors.text}"
    textColor: "{colors.wall}"
  filter-below-active:
    backgroundColor: "{colors.sticker}"
    textColor: "{colors.ink}"
  input-field:
    textColor: "{colors.ink}"
    rounded: "0"
    padding: "6px 2px"
  history-panel:
    backgroundColor: "{colors.label}"
    textColor: "{colors.ink}"
    rounded: "{rounded.panel}"
    padding: "18px 22px 16px"
  notice:
    backgroundColor: "{colors.label}"
    textColor: "{colors.ink}"
    typography: "{typography.small}"
    rounded: "{rounded.control}"
    padding: "16px 12px 10px"
  button-sign-in:
    textColor: "{colors.text-dark}"
    rounded: "{rounded.control}"
    padding: "8px 12px"
  button-sign-in-hover:
    backgroundColor: "{colors.rail-hi}"
  button-sign-up:
    backgroundColor: "{colors.label}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "8px 12px"
  button-sign-up-hover:
    backgroundColor: "{colors.hair}"
  account-avatar:
    rounded: "{rounded.wobbler}"
    size: "40px"
  markdown-cut:
    backgroundColor: "{colors.label}"
    textColor: "{colors.ink-2}"
    rounded: "{rounded.control}"
    padding: "6px 4px"
  markdown-cut-pressed:
    backgroundColor: "{colors.label}"
    textColor: "{colors.ink}"
---

# Design System: PriceWatch

## Overview

**Creative North Star: "Shelf-Edge Markdown"**

The page is a store wall. Every tracked product stands in a bay above a graphite shelf rail, and its price is a shelf-edge label standing on the bay, its bottom edge tucked under a slim graphite shelf strip. Reaching the shopper's target is the yellow markdown sticker slapped onto the bay; getting close is the red shelf wobbler on its spring. Nothing here is a SaaS card: the vocabulary is label stock, ink, rails, stickers, barcodes and SKU print.

Density is retail, not dashboard. Labels are compact and uniform so a shelf reads at a glance; the only big type on the page is the price, set like a real shelf label with a small currency symbol, a heavy whole number and a raised, underlined fraction. One variable family (Archivo) carries every role by moving its width and weight axes. The world rejects the category default of white cards with green badges and blue buttons.

Light and dark are the same store with the lights changed. The wall, bays and rails darken; label stock stays light (white by day, softer bone at night) so its ink, and every price, never changes.

**Key Characteristics:**
- A slim graphite shelf strip (18px) with a glossy top edge, tucked over the bottom of each label; bays touch so the strips read as one continuous shelf edge. Never frame a label on all sides with graphite.
- Light label stock with black ink in both themes; dark theme uses bone stock to avoid glare.
- Three signal colors, each owning one state: sticker yellow (at or below target), wobbler red (within 10% of target), laser red (a check in progress). A muted stamp red marks a sold-out product.
- Failed checks void the label (hatched stock, "Last read" tag) instead of hiding the price.
- One variable typeface; hierarchy by width and weight axes, tabular figures throughout.
- Physical motion: sticker slap, price reprint, scanner sweep, history unroll, wobbler sway.

## Colors

A neutral grey store (wall, bays, graphite rails) with light label stock, black ink, and three signal colors that each mean exactly one thing.

### Primary
- **Markdown Sticker Yellow** (sticker): the "at or below your price" signal. Fills the markdown sticker, the "At or below target" readout when the count is above zero, the "At your price" filter when pressed, the "under" gap chip on a label, receipt marks and under-target chart dots, tooltip and zone. Also used for text selection, and at 28% (`rgba(255, 210, 31, 0.28)`) as the focus wash inside label-stock inputs.

### Secondary
- **Shelf Wobbler Red** (wobbler; wobbler-dark at night): the "almost there" signal for a price within 10% of target (the `CLOSING_IN_RATIO` of 1.1). Fills the round wobbler. Its only other use is the hover text color of the Delete action.

### Tertiary
- **Scanner Laser Red** (laser): the moving scan line with its red glow that sweeps a label while "Check now" runs. Nothing else.

### Sold-Out Stamp
- **Stamp Brick** (stamp) on **Stamp Blush** (stamp-soft): a red rubber-stamp "SOLD OUT" on label stock, a 1px inset outline at 40% brick. Deliberately calmer and warmer than wobbler red so the two never read as the same signal. Used only for the "Sold out" tag on a label and the "sold out" receipt mark in history. It sits on label stock, so it does not change by theme. 5.7:1 contrast.

### Neutral
- **Thermal Label White** (label) and **Bone Label Stock** (label-bone): the label surface for labels, readouts, the add-form blank label, history panel and notices. Bone replaces white only in dark theme.
- **Label Ink** (ink), **Ink Grey** (ink-2), **Faded Print** (ink-3): text on label stock, from price and names down to shop names, SKU codes and captions. Ink does not change between themes.
- **Label Hairline** (hair, hair-bone) and **Perforation Grey** (hair-strong, hair-strong-bone): chart gridlines, skeleton bars, and the dashed perforation rules that divide a label's sections.
- **Store Wall** (wall / wall-dark), **Wall Seam** (wall-line), **Wall Hover** (wall-hover): page background, footer rule, ghost-button and filter hover.
- **Bay Back** (bay / bay-dark): the recessed display above each rail, shaded from the top.
- **Graphite Rail** (rail / rail-dark) and **Rail Highlight** (rail-hi): the shelf rails and the aisle header band.
- **Wall Text** (text, text-2 and their dark pairs): headings and secondary copy that sit directly on the wall, not on labels.
- **Fascia Grey** (fascia / fascia-dark): the shop name printed like a fascia strip when a product has no photo.

### Named Rules
**The Reserved Sticker Rule.** Solid sticker yellow as a status surface means "at or below your price" and nothing else. Selection highlight and the 28% focus wash are the only non-status uses.

**The Ink Never Changes Rule.** Label stock stays light in both themes (white by day, bone #ecebe4 at night) and ink stays #141414. Only the wall, bays, rails and wall text change with the theme.

**The One Signal Per State Rule.** Yellow is at target, wobbler red is within 10%, laser red is checking, stamp red is sold out, hatched stock is a failed check. Never mix them or swap one for another.

## Typography

**Display Font:** Archivo Variable (self-hosted via @fontsource-variable/archivo with the width axis), falling back to system-ui
**Body Font:** Archivo Variable
**Label/Mono Font:** Archivo Variable (no second family)

**Character:** One grotesque family set like retail print. Condensed and heavy for product names and headings, expanded and heaviest for prices and the wordmark, with tabular figures everywhere so prices line up.

### Hierarchy
- **Display** (850, 1.625rem, 1, width 125%): the PriceWatch wordmark in the aisle band only.
- **Headline** (850, 1.625rem, 1.1, width 88%): section headings on the wall ("Your products", the label printer).
- **Price** (850, clamp 2rem to 2.5rem, 0.9, width 106%, -0.03em): the label price. Symbol and fraction are 0.44em; the fraction is raised and underlined. The add form's price field uses the same voice at 2rem.
- **Readout value** (850, 1.375rem, width 110%): header counts.
- **Sign** (900, 1.1875rem, width 112%, uppercase): sticker headline; the wobbler uses 900 at 0.9375rem, width 110%.
- **Title** (700, 1rem, 1.2, width 82%): product names on labels, clamped with two lines reserved.
- **Body** (400, 16px, 1.5): running copy; printer intro held to 44ch.
- **Small** (0.75 to 0.8125rem): shop names, target line, notices, SKU print (0.6875rem).
- **Label** (700, 0.6875rem, 0.04 to 0.05em, uppercase): field captions, readout names, history facts and table headers.

### Named Rules
**The Width Axis Rule.** Hierarchy comes from Archivo's width and weight axes (82% to 125%, 400 to 900), never from a second typeface.

**The Shelf Price Rule.** Every price uses the label setting (small symbol, heavy whole, raised underlined fraction, tabular lining figures) and the currency's own narrow symbol, never an assumed one.

## Layout

The page is an aisle: a full-width graphite header band, then a centered store column (max 1240px, 24px side padding, 16px under 640px) with 48px between the label printer and the shelf (40px on mobile).

The shelf is a grid of bays, `repeat(auto-fill, minmax(330px, 1fr))`, with a 48px row gap and **zero column gap**. Each bay is a 176px bay display above a rail, then a row of ghost actions. Opening history inserts a full-width row under the product's shelf row (pulled up 32px, with a label-stock notch pointing at the product), so nothing on the shelf moves sideways.

The label printer is a two-column grid (intro 220 to 300px, blank label) that stacks under 900px; inside, the blank label is a two-by-two grid (link / price, name / submit) that stacks under 640px. Under 640px the three readouts become an equal three-column grid with short captions ("At target"), and filters scroll horizontally. The printer intro and the empty shelf's copy change with the account state (signed in, guest, accounts not configured); the layout does not.

The aisle band's right side holds the readouts and then the account controls (12px 16px gap): "Sign in" and "Sign up" for a guest, the avatar once signed in, nothing when accounts are not configured. Under 640px the account controls move up onto the wordmark row (pushed to the right edge) and the readouts take the full row below. There is no account page: sign-in and sign-up open over the shelf (see the Hosted Card Rule).

Spacing steps are 4, 8, 12, 16, 24, 36 and 48px, with tighter 1 to 3px nudges inside labels.

The Chrome extension popup is one bay from the shelf at a fixed 360px width, stacked top to bottom: a compact aisle band (wordmark at 1.25rem, a 30px pinned avatar), a 120px bay display with the page's product, its shelf-edge label on a graphite rail, and below the rail a blank label sheet set 12px in from the popup's edges. It imports the web app's stylesheet first and only adjusts sizes; it adds no tokens.

### Named Rules
**The One Shelf Edge Rule.** Bays touch (column-gap 0) so their rails join into one continuous shelf edge; bays are separated only by a 1px inset seam in the bay display. The 330px minimum bay width is shared with `BAY_MIN_WIDTH` in App.tsx and must change in both places together.

## Elevation & Depth

Depth is physical and soft: things lit from above cast short downward shadows. The shelf strip is a vertical gradient (highlight top, black bottom edge) with a glossy top edge and a soft drop under the shelf. Bay displays are recessed with an inset shade from the top. Labels stand on the bay panel with a soft downward shadow, their bottom 6px hidden under the strip's lip. Loose objects stuck on or lying on the shelf (readouts, sticker, wobbler, blank label, history panel, dark-theme photo tile) lift off it.

### Shadow Vocabulary
- **Shelf drop** (`box-shadow: 0 12px 18px -12px rgba(0,0,0,0.5)`): under every rail.
- **Bay recess** (`box-shadow: inset 0 14px 22px -16px var(--bay-shade), inset -1px 0 0 var(--wall-line)`): top shade and bay seam.
- **Strip gloss** (`linear-gradient(180deg, rgba(255,255,255,.2), transparent)`, 6px): the lit top edge of the shelf strip.
- **Label lift** (`0 12px 22px -14px rgba(0,0,0,.5)`): a label standing on the bay panel.
- **Stuck-on** (`box-shadow: 0 4px 8px rgba(0,0,0,0.22), 0 1px 2px rgba(0,0,0,0.2)`): the sticker. The wobbler uses `0 5px 10px rgba(0,0,0,0.25)`, readouts `0 2px 5px rgba(0,0,0,0.35)`.
- **Loose sheet** (`box-shadow: 0 18px 32px -20px rgba(0,0,0,0.55)`): blank label and history panel.
- **Pinned avatar** (`box-shadow: 0 0 0 2px #ffffff, 0 2px 5px rgba(0,0,0,0.35)`): the account avatar on the rail, a white rim plus the readout drop. The "Sign up" button carries the readout drop alone.
- **Scanner glow** (`box-shadow: 0 0 10px 3px rgba(255,43,43,0.45)`): laser line only.

### Named Rules
**The Lit From Above Rule.** Shadows are soft, fall downward, and belong to physical objects. No hard offset shadows, no colored glows except the scanner.

**The Hosted Card Rule.** Sign-in and sign-up are Clerk's own stock card in a modal over the shelf, outside the PriceWatch system. It is themed only through Clerk's theme-editor variables (light and dark, `frontend/src/auth/appearance.ts`); the app never restyles its card, inputs, buttons or colors. Global input and focus rules are wrapped in `:where()` (zero specificity) so they cannot leak into it. Only what PriceWatch draws around it (the aisle controls, the avatar rim, the guest notice) belongs to the system.

## Shapes

Label stock has barely-rounded corners (2px); controls and readouts 3px; larger sheets (blank label, history panel, photo tile) 4px. The sticker is a rounder 7px rectangle rotated -6deg; the wobbler is a 92px disc on a 24px spring, rotated -4deg. Rails are square. Inputs are underlines only (square, 2px ink rule). Section breaks inside labels are 1px dashed perforations. Void states use a -45deg hatch; the history notch is a small clipped triangle.

## Components

### Buttons
Printed and plain: ink blocks on label stock, quiet text on the wall.
- **Shape:** 3px corners, 1px border slot, 6px icon gap.
- **Ink (primary):** ink fill, white text, 8px 12px (large 13px 20px at 1rem 750 width 96%; small 6px 10px at 0.8125rem).
- **Hover / Active:** ink goes to pure black and lifts 1px; active presses 1px down; 150ms with the house ease-out.
- **Outline (on labels):** transparent with a perforation-grey border; hover fills with hairline grey.
- **Ghost (bay actions):** wall-text-2 at 0.8125rem, 6px 8px; hover fills wall-hover. The open History toggle keeps that fill. Delete turns wobbler red on hover.
- **Sign in (aisle band):** a quiet text button in rail text color (#eceeef), 0.875rem 650; hover fills rail highlight.
- **Sign up (aisle band):** printed on white label stock like the readouts beside it: ink text, 750, readout drop; hover fills hairline grey and lifts 1px, active presses 1px down. Deliberately not sticker yellow (the Reserved Sticker Rule).
- **Add form submit:** the large ink button reads "Check the price" for a guest and "Start tracking" for a signed-in account ("Reading the page…" while it works).
- **Disabled:** 55% opacity.

### Chips (filters)
- **Style:** 3px, 1px wall-line border, wall-text-2, 0.8125rem 650, with a bold count at 75% opacity.
- **State:** pressed fills with wall text color (inverted); the "At your price" filter fills sticker yellow instead. Filters with zero matches are hidden.

### Readouts (aisle header)
Small labels on the header rail: white stock, 3px, uppercase caption over an 850-weight value. "Tracking / At or below target / Last check", with the middle caption shortened to "At target" under 640px. The middle readout turns sticker yellow when anything is at target.

### Shelf-Edge Label (signature)
Label stock standing on the bay, tucked under the shelf strip: name block (condensed title plus shop host) beside the price slot; a dashed perforation, then "Your price" with a gap chip ("X under" on sticker yellow, "X to go" plain); lowest/highest; and a foot with a per-product generated barcode and SKU line (`#0001 · EUR · checked 2 hours ago`). Name lines are reserved so labels on a rail stay level.
- **Reprint:** when a price changes the price slides in from above behind a clip (560ms).
- **Scanning:** a laser line sweeps back and forth while checking.
- **Void:** a failed check hatches the stock, fades the price to faded-print grey and adds a "Last read" ink tag; a void-tape notice under the rail explains why. The last known price stays on the label.
- **Sold out:** a stamp-red "Sold out" tag above the price (a plain ink "Pre-order" tag for pre-orders). The shop's listed price stays on the label.
- **Guest (read once):** a product added without an account was read once and is not tracked: the SKU line reads "read once <time>", the bay has no "Check now" or "History" actions, and the guest notice sits under the rail.

### Markdown Sticker (signature)
Sticker yellow, 7px, rotated -6deg at the bay's bottom right: "AT YOUR PRICE" plus "X under" or "Right on target". It slaps on (620ms: drops from above, overshoots to 0.97 scale, settles), staggered 90ms per bay.

### Shelf Wobbler (signature)
Wobbler red disc on a spring at the bay's bottom left: "ALMOST / amount / to go". On bay hover it sways on its spring with a damped keyframe swing (900ms). This is intentional physical behavior, not bounce easing on a UI transition.

### Inputs / Fields
- **Style:** written on label stock. No box: 2px ink underline, transparent fill, 1.0625rem 600, faded-print placeholder. Captions are small uppercase labels above.
- **Focus:** 28% sticker wash plus a doubled 2px ink underline (160ms). Elsewhere focus is a 2px outline at 2px offset in the local text color (light on the rail, ink on labels).
- **Scope:** these input rules are zero-specificity (`:where()`) so they style PriceWatch's own fields only and never Clerk's hosted card.

### Notices
Label-stock slip, 3px, with a 6px hatched void-tape strip across the top and an icon. The page-level banner version carries a small ink Retry button.
- **Failed check (under a label) and failed add (under the form):** three parts. A plain headline (750, 0.875rem, width 92%) that names what happened in the shopper's words ("startech.com.bd blocks automated checks", "Couldn't reach …"); the backend's evidence as body text ("The shop showed a Cloudflare bot check instead of the product page."); then, below a 1px dashed perforation, what happens next in ink-2 ("Trying again in 15 minutes. The price shown is the last one we read.").
- **Icon by reason:** blocked (circle with a bar) for a shop that refuses automated checks, clock for a temporary failure that will be retried early, alert for everything else. The label itself stays voided with its "Last read" tag in every case.
- **Guest limit (under the form):** when a guest reaches the product limit, the same failed-add notice carries a small ink "Sign up" button.
- **Guest notice (under a guest's label):** a plain slip without void tape, since nothing failed: tag icon and one line ("Read once, not tracked yet", 750 headline weight) with a small ink "Sign up to keep tracking" button pushed right; on a narrow card the button wraps to its own line while the text keeps the icon's row. When accounts are not configured the line reads "Read once. Tracking needs accounts, which are not set up on this PriceWatch." and there is no button.
- Card notices are `role="status"` (they persist and refresh with polling); the guest notice is `role="note"`; the add-form notice is `role="alert"`.

### Account Button
The signed-in avatar, a 40px disc pinned to the aisle rail with a 2px white rim and the readout drop. Focus is the house 2px outline in the rail text color. It opens Clerk's own account menu, which, like the sign-in card, is outside the system.

### History Panel
A label-stock sheet that unrolls downward (420ms clip), with facts (uppercase captions over 800-weight values), an ink/label segmented Chart/Table switch, and a step chart: ink step line, dashed ink target line, sticker-tinted zone under the target, sticker-filled dots at or under target. The table is a receipt with dashed rules and sticker marks.

### Extension Popup
The web app's label, rail, sticker, buttons and notices, composed as a single bay (see Layout).
- **Label on the rail:** the current page's product in the shelf price setting. While the page is read the laser sweeps it and the name lines are unprinted hairline bars. A product already on the shelf carries an ink "On your shelf" tag above the price; when today's price is at or below the shopper's price the markdown sticker slaps onto the bay's corner and the product steps aside.
- **Blank label sheet (the desk):** label stock, 4px, loose-sheet shadow plus a 1px 6% ink edge, 16px padding, 12px gap. "Alert me at or below" caption over a price field written in the price voice (850, 2.125rem, width 106%, small raised symbol, currency code at the right) on the 2px ink underline with the 28% sticker focus wash; then the markdown click-stops; a dashed perforation and the drop line ("$32.90 below today's $329.00"); a full-width large ink action; and the barcode with a "New label" SKU line. Once on the shelf, the sheet shows the shopper's price and gap chip instead, and omits the "under" chip when the sticker is already on the bay.
- **Markdown click-stops:** four equal outline buttons (−5, −10, −15, −20%) printed on the sheet, 3px, perforation-grey border, ink-2, tabular figures. Pressed is a 2px ink rule (border plus 1px inset) with ink text at 800, never a fill, so the only solid ink on the sheet is the primary action.
- **Problems:** the void-tape notice runs full-bleed inside the sheet (square corners, no shadow), with a small ink Retry or "Open your shelf" button. The guest limit swaps the action to "Sign up to watch more"; a shop that blocks checks shows the notice and "Open your shelf" with no price field.

### Icons
One authored set on a 24px grid, 1.75 stroke, round caps and joins, currentColor, used at 16 to 18px. `clock` and `blocked` share the same 8.5px-radius circle.

## Do's and Don'ts

### Do:
- **Do** put every price on label stock in the shelf price setting, with tabular lining figures and the currency's narrow symbol.
- **Do** keep label stock light in both themes (white #ffffff, bone #ecebe4 in dark) and ink at #141414.
- **Do** use sticker yellow for at or below target, wobbler red for within 10% of target, laser red for an active check, and the hatched void label with a "Last read" tag for a failed check.
- **Do** keep bays touching (column-gap 0) and keep the 330px bay minimum in sync between index.css and `BAY_MIN_WIDTH` in App.tsx.
- **Do** separate label sections with 1px dashed perforation rules, not boxes.
- **Do** leave Clerk's sign-in and sign-up card stock, themed only by its theme-editor variables, and keep global input and focus rules inside `:where()` so they never reach it.
- **Do** make motion physical and short (slap 620ms, reprint 560ms, unroll 420ms, controls 150 to 160ms) on `cubic-bezier(0.16, 1, 0.3, 1)`, and collapse it under reduced motion (the scanner stops centered).

### Don't:
- **Don't** use white SaaS cards, green status badges or blue buttons.
- **Don't** use sticker yellow for success, emphasis or decoration unrelated to the target price.
- **Don't** darken or tint label stock in dark theme, or change ink color by theme.
- **Don't** add gaps or gutters between bays, or change the bay minimum in one file only.
- **Don't** hide or blank the last known price when a check fails; void the label instead.
- **Don't** add a second typeface; use Archivo's width and weight axes.
- **Don't** use hard offset shadows or colored glows other than the scanner line.
- **Don't** use emoji or glyph characters as icons; use the authored stroke set.
- **Don't** restyle Clerk's hosted card with PriceWatch tokens or `.cl-*` overrides; the only exception is the 40px avatar rim in the aisle band.
