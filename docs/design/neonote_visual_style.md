# NeoNote Visual Style (Milestone 3)

## Product Mood
- calm
- pale
- academic
- paper-like
- low-distraction
- pen-first

## Visual Principles
1. Canvas is the primary scene; controls stay secondary.
2. Note objects look light and readable, never debug-panels.
3. Inline table should feel embedded in notes, not a spreadsheet app.
4. Focus/selection states are visible but quiet.

## Core Tokens
- Paper background: warm off-white
- Text container surface: near-white
- Subtle border: low-contrast neutral
- Selected border: muted cool accent
- Active cell outline: soft accent (thin)
- Table grid: pale gray, thin lines
- Toolbar background: muted warm neutral
- Text colors:
  - primary: high readability
  - secondary: subdued labels

## Normal Mode Rules
- No pressure/event debug overlays.
- No stress/build diagnostics visible in main editing surface.
- No harsh dark cards or thick borders.

## Legacy Boundary
- Standalone legacy TableBlock remains compatibility-only.
- Normal visual language follows TextContainer + inline table flow.
