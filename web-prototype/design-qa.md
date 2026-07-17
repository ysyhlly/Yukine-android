# Design QA

## Evidence

- Source visual truth: `C:/Users/31283/.codex/visualizations/2026/07/16/019f6cff-a798-7ee1-9381-fedf7e2498eb/design-comparison-final.png` (reference Home and Library panels).
- Browser-rendered implementation: `qa-home-final.png` and `qa-library-final.png`.
- Combined full-view comparison: `qa-home-comparison.png`.
- Viewport: 390 × 844.
- State: Home default, player paused; Library default, player paused.
- Focused-region comparison: not required. The comparison keeps both Home panels at their native 390 × 844 scale, so greeting hierarchy, card geometry, artwork crop, controls, typography, and bottom navigation remain legible in one view.

## Findings

- No actionable P0, P1, or P2 differences remain.
- Typography: Noto Sans CJK SC and Outfit load locally; greeting, section titles, metadata, and navigation labels keep the intended hierarchy without clipping or awkward wrapping.
- Spacing and layout: 390 × 844 frame, 20 px side margins, section rhythm, right-side continuation artwork, recent four-column shelf, stacked recommendation rows, fixed player, and navigation remain aligned and unobstructed.
- Colors and tokens: cool white / pale blue surfaces, blue accent, separators, translucent player/nav surfaces, and readable secondary text follow the reference direction.
- Image quality: seven generated 1024 × 1024 PNG covers are used as real image assets with correct square crops and no placeholder or CSS-drawn album art.
- Copy and content: greeting, search, continuation queue, recent albums, recommendations, library counts, sources, and player metadata are coherent and match the requested content.
- Icons: Phosphor icons are consistent in weight, scale, alignment, and active-state behavior.
- Accessibility: semantic headings and buttons, search labels, alt text, keyboard-visible focus treatment, practical mobile tap targets, and reduced-motion support are present.

## Comparison History

1. Initial pass — blocked.
   - P1: Home greeting hierarchy was inverted, making the question the dominant headline instead of “早上好”.
   - P1: The continuation card used a dark full-background image instead of a light card with copy left and artwork right.
   - P1: “今天听什么” rendered as two horizontal tiles instead of two stacked recommendation rows.
   - P2: Mini-player artwork and controls differed from the reference treatment.
2. Fixes applied.
   - Restored the greeting headline/subtitle hierarchy.
   - Rebuilt the continuation card as a light surface with a three-cover right-side artwork strip, blue play control, and progress line.
   - Restored the stacked recommendation list and compacted vertical spacing.
   - Updated the mini-player to round artwork with previous/play controls and preserved the floating glass treatment.
   - Normalized all cover assets to 1024 × 1024 and aligned focus/active states with the blue visual system.
3. Post-fix pass — passed.
   - Evidence: `qa-home-comparison.png`, `qa-home-final.png`, and `qa-library-final.png`.

## Functional Review

- Tested Home ↔ Library navigation and active states.
- Tested Home search input and submit feedback.
- Tested queue bottom sheet open/close.
- Tested mini-player play/pause state change.
- Confirmed the production Vite build succeeds.
- Confirmed browser console errors/warnings: none.

final result: passed
