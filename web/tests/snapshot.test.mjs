import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { validateSnapshot, parseSnapshotText, parseSnapshotFile, MAX_SNAPSHOT_BYTES } from '../lib/snapshot.ts';

const demo = JSON.parse(await readFile(new URL('../public/demo-report.json', import.meta.url), 'utf8'));
const copy = () => structuredClone(demo);

test('anonymous demo conforms to the real Android parent export contract', () => {
  const report = validateSnapshot(demo);
  assert.equal(report.children.length, 2);
  assert.equal(report.children[0].week.at(-1).date, report.day);
});

test('rejects another product, hidden extra fields and duplicate child IDs', () => {
  assert.throws(() => validateSnapshot({...demo,product:'Other Quest'}), /来源或版本/);
  assert.throws(() => validateSnapshot({...demo,credentials:'secret'}), /unexpected fields/);
  const repeated = copy(); repeated.children[1].id = repeated.children[0].id;
  assert.throws(() => validateSnapshot(repeated), /duplicate/);
});

test('rejects forged future day, broken seven-day dates and inconsistent completion', () => {
  const invalidDay = copy(); invalidDay.day = '2026-02-30';
  assert.throws(() => validateSnapshot(invalidDay), /invalid date/);
  const brokenWeek = copy(); brokenWeek.children[0].week[2].date = '2026-09-25';
  assert.throws(() => validateSnapshot(brokenWeek), /not consecutive/);
  const falseCompletion = copy(); falseCompletion.children[0].completed_today = false;
  assert.throws(() => validateSnapshot(falseCompletion), /today does not match/);
});

test('rejects impossible counts and schema smuggling into nested objects', () => {
  const counts = copy(); counts.children[0].reviewing = counts.children[0].introduced + 1;
  assert.throws(() => validateSnapshot(counts), /inconsistent learning/);
  const weekCount = copy(); weekCount.children[1].daily_stamps = 0;
  assert.throws(() => validateSnapshot(weekCount), /inconsistent week/);
  const extra = copy(); extra.children[0].week[0].approvedReward = true;
  assert.throws(() => validateSnapshot(extra), /unexpected fields/);
});

test('refuses a file at the 1 MB limit before parsing and invalid UTF-8', async () => {
  const oversized = new File([new Uint8Array(MAX_SNAPSHOT_BYTES)], 'large.json', {type:'application/json'});
  await assert.rejects(parseSnapshotFile(oversized), /under 1 MB/);
  const invalidUtf8 = new File([new Uint8Array([0xff, 0xfe])], 'bad.json');
  await assert.rejects(parseSnapshotFile(invalidUtf8), /UTF-8/);
});

test('malformed JSON and prototype-pollution fields are not accepted', () => {
  assert.throws(() => parseSnapshotText('{"product":'), /invalid JSON/);
  const polluted = JSON.stringify(demo).replace('"product":', '"__proto__":{"polluted":true},"product":');
  assert.throws(() => parseSnapshotText(polluted), /unexpected fields/);
  assert.equal({}.polluted, undefined);
});
