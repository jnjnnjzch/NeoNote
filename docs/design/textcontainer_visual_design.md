# TextContainer Visual Design (Milestone 3)

## Goal
TextContainer should feel like a calm note object resting on paper.

## Implemented Style
- Surface: near-white (`NeoNoteVisualTokens.containerSurface`).
- Border:
  - default: subtle (`subtleBorder`, thin)
  - focused/selected: muted accent (`selectedBorder`)
- Shape: soft rounded corners (`MaterialTheme.shapes.medium`).
- Padding: internal breathing room for paragraph/table flow.

## Content Flow
- Paragraph before inline table.
- Inline table block.
- Paragraph after inline table.
- Natural vertical spacing; table is embedded, not floating separately.

## Non-goals in this milestone
- Full text rich-format toolbar.
- block-level resize interaction for normal mode.
- advanced container hierarchy.
