# E-Ink guidelines

- Dedicated `EInkTheme`: black text, white background, solid borders; no gradients, shimmer, animation, autoplay, ripples or automatic paging.
- Minimum 56 dp primary touch targets; base text 20 sp; large character glyphs. Meaning is not encoded solely by color.
- One finite task at a time; explicit Next/Back; bounded vertical scrolling only for accessibility on small screens, never infinite content.
- A wrong answer gives calm static feedback. No flashing, countdown, streak loss or rapid score changes.
- Paper is the writing surface. Device records self-report or parent observation and does not claim handwriting evaluation.
- Respect system font scaling. Test portrait/landscape, font scale 1.0/1.3/1.5 and slow refresh.
- BOOX hardware gate: verify glyphs, touch reach, ghosting, refresh mode and resume after sleep. Platform rendering tests cannot certify E-Ink behavior.
- No proprietary refresh calls without a documented supported BOOX SDK; parents may choose device refresh settings manually.
