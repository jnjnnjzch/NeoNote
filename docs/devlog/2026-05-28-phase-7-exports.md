# Phase 7: Exports

## Scope

- Added export pipeline in drawing view model.
- Implemented export outputs:
  - PDF
  - HTML + assets
  - Markdown + assets
  - native zip-based `.ticnote` archive
- Added top-bar `Export` trigger button.

## Output Location

- Exports are written to app internal files directory:
  - `files/exports/<timestamp>/...`

## Notes

- Exports include structured table content and stroke counts.
- Ink and table data remain separate in source persistence.
