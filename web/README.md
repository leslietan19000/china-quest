# China Quest 家长看板 · Panel familiar

This is a local, read-only view of the Android **parent export** JSON. It does not connect to the Android device, Supabase, or any account. It has no upload endpoint, analytics SDK, remote fonts, or browser storage. The selected file is validated and read into this tab's memory; refresh or close the tab to clear it. The demo button loads only `public/demo-report.json`, which contains synthetic, anonymous data and is prominently marked as a demo.

From `web/` with Node 24:

```text
npm ci --ignore-scripts
npm test
npm run typecheck
npm run build
npm run start
```

Open `http://127.0.0.1:3210`. `npm run dev` also binds only `127.0.0.1:3210`. The `scripts/next.mjs` runner sets `NEXT_TELEMETRY_DISABLED=1` for dev, build and start. No persistent server is started by the repository itself.

On BOOX, a parent unlocks the PIN area and exports the report. Here, choose that `.json` file (strictly less than 1 MB). The dashboard checks version, source, dates, all child fields, seven consecutive days per child, duplicate IDs, count ranges and internal consistency before displaying anything. A failed import clears the previous report. Displayed dates are the **recorded export date**, not a live status. Settings must still be changed in the BOOX parent area.

The household overview shows each child's own numbers without ranking them. “Near 7 days completed” counts only days marked complete; partial days remain visible in the child detail. The content guide describes current learning evidence. Rewards, seasons, creator and Builder are labeled future roadmap, with no fake actions.

There is deliberately no pairing or cloud sync in this dashboard. The backend contract in `../backend` is a separately tested local prototype; neither component activates remote transport.
