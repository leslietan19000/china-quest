import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';

const ids = {
  familyA: '10000000-0000-4000-8000-000000000001', familyB: '10000000-0000-4000-8000-000000000002',
  parentA: '20000000-0000-4000-8000-000000000001', parentB: '20000000-0000-4000-8000-000000000002',
  childA: '30000000-0000-4000-8000-000000000001', childASibling: '30000000-0000-4000-8000-000000000002',
  childB: '30000000-0000-4000-8000-000000000003',
  childUserA: '40000000-0000-4000-8000-000000000001', childUserASibling: '40000000-0000-4000-8000-000000000002',
  childUserB: '40000000-0000-4000-8000-000000000003',
  event: '50000000-0000-4000-8000-000000000001', forged: '50000000-0000-4000-8000-000000000002',
};
let db;

async function asUser(userId, run) {
  await db.exec('reset role');
  await db.query("select set_config('request.jwt.claim.sub', $1, false)", [userId]);
  await db.exec('set role authenticated');
  try { return await run(); } finally { await db.exec('reset role'); }
}
async function rows(sql, params = []) { return (await db.query(sql, params)).rows; }
async function ingest(eventId, familyId, childId, kind, outcome = 'CORRECT') {
  return rows('select public.ingest_sync_event($1,$2,$3,$4,$5,$6,$7,$8) as result',
    [eventId, familyId, childId, kind, '2026-09-28', 'hanzi-1', 'RECOGNITION', outcome]);
}

before(async () => {
  db = new PGlite();
  await db.exec(`create role authenticated; create role anon; create schema auth;
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub', true),'')::uuid $$;
    grant usage on schema auth to authenticated; grant execute on function auth.uid() to authenticated;`);
  await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
  await db.query(`insert into public.families values ($1,'Family A'),($2,'Family B')`,[ids.familyA,ids.familyB]);
  await db.query(`insert into public.parents values ($1,$2),($3,$4)`,[ids.parentA,ids.familyA,ids.parentB,ids.familyB]);
  await db.query(`insert into public.children(id,family_id,display_name) values
    ($1,$2,'A'),($3,$2,'A sibling'),($4,$5,'B')`,[ids.childA,ids.familyA,ids.childASibling,ids.childB,ids.familyB]);
  await db.query(`insert into public.child_accounts values ($1,$2,$3),($4,$2,$5),($6,$7,$8)`,
    [ids.childUserA,ids.familyA,ids.childA,ids.childUserASibling,ids.childASibling,ids.childUserB,ids.familyB,ids.childB]);
  await db.query(`insert into public.characters(id,hanzi,pinyin,source_kind,source_version,source_url,content_hash,approved_version_hash)
    values ('hanzi-1','一','yi','UNIHAN','17.0','https://www.unicode.org/', $1, $1),
           ('draft-1','二','er','CURATED','1','https://example.invalid/source', $2, null)`,['a'.repeat(64),'b'.repeat(64)]);
  await db.query(`insert into public.child_learning_profiles(family_id,child_id) values ($1,$2),($1,$3),($4,$5)`,
    [ids.familyA,ids.childA,ids.childASibling,ids.familyB,ids.childB]);
  await db.query(`insert into public.family_reward_budgets(family_id) values ($1),($2)`,[ids.familyA,ids.familyB]);
});
after(async () => { await db?.close(); });

test('RLS is enabled for every public table and family parents cannot see another family', async () => {
  const unprotected = await rows(`select c.relname from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind='r' and not c.relrowsecurity`);
  assert.deepEqual(unprotected, []);
  await asUser(ids.parentA, async () => {
    assert.deepEqual((await rows('select display_name from public.families')).map(r=>r.display_name),['Family A']);
    assert.equal((await rows('select id from public.children')).length,2);
    assert.equal((await rows('select * from public.parents')).length,1);
    assert.equal((await rows('select * from public.child_accounts')).length,0);
    assert.equal((await rows('select * from public.children where family_id=$1',[ids.familyB])).length,0);
    assert.equal((await rows('update public.children set daily_target=2 where id=$1 returning id',[ids.childB])).length,0);
    assert.equal((await rows('update public.children set daily_target=2 where id=$1 returning id',[ids.childA])).length,1);
  });
});

test('a child sees its own profile and events, never a sibling or another family', async () => {
  await asUser(ids.childUserA, async () => {
    assert.deepEqual((await rows('select id from public.children')).map(r=>r.id),[ids.childA]);
    assert.deepEqual((await rows('select child_id from public.child_accounts')).map(r=>r.child_id),[ids.childA]);
    assert.deepEqual((await rows('select child_id from public.child_learning_profiles')).map(r=>r.child_id),[ids.childA]);
    assert.equal((await rows('select * from public.family_reward_budgets')).length,0);
    assert.equal((await rows('select * from public.device_child_links')).length,0);
  });
});

test('family and child reassignment cannot be written by a client', async () => {
  await asUser(ids.parentA, async () => {
    await assert.rejects(rows('update public.children set family_id=$1 where id=$2',[ids.familyB,ids.childA]));
    await assert.rejects(rows('insert into public.child_accounts values ($1,$2,$3)',[ids.parentA,ids.familyA,ids.childASibling]));
    await assert.rejects(rows('update public.parents set family_id=$1 where user_id=$2',[ids.familyB,ids.parentA]));
  });
  await asUser(ids.childUserA, async () => {
    assert.equal((await rows('update public.children set daily_target=10 where id=$1 returning id',[ids.childA])).length,0);
    await assert.rejects(rows('update public.child_learning_profiles set character_level=100 where child_id=$1',[ids.childA]));
  });
});

test('a child cannot forge parent evidence, rewards or derived mastery', async () => {
  await asUser(ids.childUserA, async () => {
    await assert.rejects(ingest(ids.forged,ids.familyA,ids.childA,'PARENT_CHECK'));
    await assert.rejects(rows('insert into public.sync_events(event_id,family_id,child_id,actor_user_id,kind,occurred_on,character_id,skill,outcome) values ($1,$2,$3,$4,$5,$6,$7,$8,$9)',
      [ids.forged,ids.familyA,ids.childA,ids.childUserA,'PARENT_CHECK','2026-09-28','hanzi-1','RECOGNITION','CORRECT']));
    assert.equal((await rows('update public.family_reward_budgets set max_weekly_choices=7 where family_id=$1 returning family_id',[ids.familyA])).length,0);
    await assert.rejects(rows('insert into public.child_character_mastery(family_id,child_id,character_id,due_on) values ($1,$2,$3,$4)',
      [ids.familyA,ids.childA,'hanzi-1','2026-09-28']));
  });
  await asUser(ids.parentA, async () => {
    assert.equal((await ingest(ids.forged,ids.familyA,ids.childA,'PARENT_CHECK'))[0].result,'accepted');
    assert.equal((await rows('select evidence_source from public.sync_events where event_id=$1',[ids.forged]))[0].evidence_source,'PARENT');
  });
});

test('sync replay is idempotent and a changed payload with the same UUID is rejected', async () => {
  await asUser(ids.childUserA, async () => {
    assert.equal((await ingest(ids.event,ids.familyA,ids.childA,'QUIZ_ATTEMPT'))[0].result,'accepted');
    assert.equal((await ingest(ids.event,ids.familyA,ids.childA,'QUIZ_ATTEMPT'))[0].result,'duplicate');
    await assert.rejects(ingest(ids.event,ids.familyA,ids.childA,'QUIZ_ATTEMPT','WRONG'),/SYNC_EVENT_CONFLICT/);
    assert.equal((await rows('select count(*)::int as count from public.sync_events where event_id=$1',[ids.event]))[0].count,1);
    assert.equal((await rows('select evidence_source from public.sync_events where event_id=$1',[ids.event]))[0].evidence_source,'QUIZ');
    await assert.rejects(ingest('50000000-0000-4000-8000-000000000099',ids.familyA,ids.childASibling,'QUIZ_ATTEMPT'));
  });
});

test('content approval is tied to the content hash and drafts are hidden from children', async () => {
  await asUser(ids.parentA, async () => assert.equal((await rows('select id from public.characters')).length,2));
  await asUser(ids.childUserA, async () => assert.deepEqual((await rows('select id from public.characters')).map(r=>r.id),['hanzi-1']));
  await assert.rejects(rows(`insert into public.characters(id,hanzi,pinyin,source_kind,source_version,source_url,content_hash,approved_version_hash)
    values ('bad','三','san','CURATED','1','https://example.invalid', $1, $2)`,['c'.repeat(64),'d'.repeat(64)]));
});

test('derived review and quiz rows cannot point to another child event', async () => {
  const eventId = '50000000-0000-4000-8000-000000000010';
  await db.query(`insert into public.sync_events(event_id,family_id,child_id,actor_user_id,kind,occurred_on,character_id,skill,outcome)
    values ($1,$2,$3,$4,'QUIZ_ATTEMPT','2026-09-28','hanzi-1','RECOGNITION','CORRECT')`,
    [eventId,ids.familyA,ids.childA,ids.childUserA]);
  await assert.rejects(rows(`insert into public.review_events(id,family_id,child_id,sync_event_id,character_id,reviewed_on,outcome)
    values ($1,$2,$3,$4,'hanzi-1','2026-09-28','CORRECT')`,
    ['60000000-0000-4000-8000-000000000001',ids.familyA,ids.childASibling,eventId]));
  await assert.rejects(rows(`insert into public.quiz_attempts(id,family_id,child_id,sync_event_id,character_id,attempted_on,correct)
    values ($1,$2,$3,$4,'hanzi-1','2026-09-28',true)`,
    ['60000000-0000-4000-8000-000000000002',ids.familyA,ids.childASibling,eventId]));
});
