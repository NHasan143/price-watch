---
version: 1
slug: "frontend-src-app-tsx"
primary_target: "frontend/src/App.tsx"
related_targets: ["frontend/src/index.css","frontend/src/components"]
---

# PriceWatch dashboard (frontend/src/App.tsx)

Scope: the single-page PriceWatch app (header counts, add form, tracked products, history, edit/delete). Mode: Operate.
Task: glance "is anything cheap enough yet?", add a link with a target, check now, read history, adjust target.
States: loading, empty, API unreachable, per-product check failed, closing in (within 10 % of target), at target.
Constraints: backend API unchanged; mixed currencies; light and dark; keyboard + reduced motion.
Build path: code-led (no image generation available).

## Direction contract

THESIS: Every tracked product is a shelf-edge label clipped to a store shelf; reaching your target is the yellow markdown sticker landing on it. Refuses the category default of white SaaS cards with green badges and blue buttons.

OWN-WORLD: Bright thermal label stock on graphite shelf rails in a clear plastic ticket strip; ink-black condensed product names, heavy expanded price numerals with small currency; fluorescent markdown-yellow sticker reserved for at-target; shelf-wobbler red tab for closing in; hatched void label for failed checks; barcode strip and SKU/date code in small print. Archivo (width + weight axes) everywhere, tabular figures.

STORY: The shopper scans the shelf, sees which labels carry a sticker, reads now/was/target at a glance, prints a new label from a link, and checks history when they want proof.

FIRST VIEWPORT: Graphite aisle band: PriceWatch wordmark left, three readouts right (on the shelf, marked down, last check). Below, the label printer: one wide blank label with the link field as the name line and "your price" in the big price slot, Start tracking beside it. Then the shelf: bays of product image standing above a rail, label clipped on the rail edge.

FORM: Shelf-edge markdown labels, position 1 on the ordered list (IMPECCABLE'S PICK); seed key c435e844. Signature interaction: the sticker slap when a check lands at or below target; labels reprint (short paper slide) when a price changes.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
