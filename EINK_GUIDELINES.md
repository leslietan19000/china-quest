# E-Ink guidelines

- Dedicated `EInkTheme`: black text, white background, solid borders; no gradients, shimmer, animation, autoplay, ripples or automatic paging.
- Minimum 56 dp primary touch targets; base text 20 sp; large character glyphs. Meaning is not encoded solely by color.
- One finite task at a time; explicit Next/Back; bounded vertical scrolling only for accessibility on small screens, never infinite content.
- A wrong answer gives calm static feedback. No flashing, countdown, streak loss or rapid score changes.
- Paper is the writing surface. Device records self-report or parent observation and does not claim handwriting evaluation.
- Respect system font scaling. Test portrait/landscape, font scale 1.0/1.3/1.5 and slow refresh.
- BOOX hardware gate: verify glyphs, touch reach, ghosting, refresh mode and resume after sleep. Platform rendering tests cannot certify E-Ink behavior.
- No proprietary refresh calls without a documented supported BOOX SDK; parents may choose device refresh settings manually.

## Child-requested color and interaction preview

Version 0.3 adds optional, finite creative pages at the family's request. Learning pages retain the static monochrome theme. Creative pages use six named, solid color swatches with a selection checkmark and outline, so grayscale devices can still distinguish controls. There are no gradients, timed frames, autoplay or moving rewards.

Tap-fill is the default for slower refresh. Brush strokes follow deliberate finger/stylus movement only; no timer redraws the canvas. A completed stroke is saved on lift. Cancellation, losing the active pointer and detaching the view discard the unfinished stroke. Undo includes clearing the canvas. The character's black outline remains visible, and painting is clipped to the glyph. This is coloring, not stroke-order teaching or handwriting assessment.

Scenes are a small sequence of static drawings. A matching down/up on a >=56dp object target, an accessibility click or the large text action button advances one step. Each scene stops after 2–3 actions. Children explicitly choose whether to replay. No action waits for AI or network access. A finite scroll remains available for large system fonts.

BOOX gate for this preview: compare tap-fill and brush response in the family's normal refresh mode; inspect muted colors/black outline, stray strokes while scrolling, ghosting after scene changes and persistence after sleep. Software tests are not a measurement of physical E-Ink refresh or stylus latency.
