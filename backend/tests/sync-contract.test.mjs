import test from 'node:test';
import assert from 'node:assert/strict';
import { validateSyncEvent, toIngestArgs, fromAndroidEnvelope } from '../sync-contract.ts';

const valid = {
  eventId:'50000000-0000-4000-8000-000000000001', familyId:'10000000-0000-4000-8000-000000000001',
  childId:'30000000-0000-4000-8000-000000000001', kind:'QUIZ_ATTEMPT', occurredOn:'2026-09-28',
  characterId:'hanzi-1', skill:'RECOGNITION', outcome:'CORRECT',
};
test('validated event maps only typed fields to the RPC', () => {
  assert.equal(validateSyncEvent(valid).eventId,valid.eventId);
  assert.deepEqual(Object.keys(toIngestArgs(validateSyncEvent(valid))).sort(),
    ['p_event_id','p_family_id','p_child_id','p_kind','p_occurred_on','p_character_id','p_skill','p_outcome'].sort());
});
test('client cannot claim parent evidence, approval, reward or derived mastery', () => {
  for (const extra of [{evidenceSource:'PARENT'},{approvedReward:true},{mastery:100},{actorUserId:'forged'}]) {
    assert.throws(()=>validateSyncEvent({...valid,...extra}),/Unexpected sync event fields/);
  }
});
test('invalid calendar day and assessment shape are rejected before transport', () => {
  assert.throws(()=>validateSyncEvent({...valid,occurredOn:'2026-02-30'}),/Invalid date/);
  assert.throws(()=>validateSyncEvent({...valid,kind:'DAILY_COMPLETE'}),/Daily completion/);
  assert.throws(()=>validateSyncEvent({...valid,kind:'PARENT_CHECK',outcome:'EXPOSURE'}),/Exposure/);
});

const mapping = {localChildId:'child-a',familyId:valid.familyId,serverChildId:valid.childId};
const android = {
  id:valid.eventId, child_id:'child-a', kind:'LEARNING_EVIDENCE', day:'2026-09-28',
  payload:{kind:'FIND',character_id:'hanzi-1',cursor:5,outcome:'CORRECT',skill:'RECOGNITION',
    source:'QUIZ',effective_date:'2026-09-28',session_date:'2026-09-28',algorithm_version:1},
  created_at:'2026-09-28T10:00:00Z',schema_version:1,
};
test('real Android quiz envelope maps local child through trusted paired UUIDs', () => {
  assert.deepEqual(fromAndroidEnvelope(android,mapping),valid);
  const read = {...android,payload:{...android.payload,kind:'READ',source:'SELF',skill:'PRONUNCIATION'}};
  assert.equal(fromAndroidEnvelope(read,mapping).kind,'LEARNING_EVIDENCE');
  const exposure = {...android,payload:{...android.payload,kind:'LEARN',source:'SELF',outcome:'EXPOSURE'}};
  assert.equal(fromAndroidEnvelope(exposure,mapping).outcome,'EXPOSURE');
  const complete = {...android,kind:'DAILY_COMPLETE',payload:{task_count:12}};
  assert.deepEqual([fromAndroidEnvelope(complete,mapping).kind,fromAndroidEnvelope(complete,mapping).characterId],['DAILY_COMPLETE',null]);
});
test('Android cannot forge PARENT, sibling mapping or settings transport', () => {
  assert.throws(()=>fromAndroidEnvelope({...android,payload:{...android.payload,source:'PARENT'}},mapping),/PARENT/);
  assert.throws(()=>fromAndroidEnvelope(android,{...mapping,localChildId:'child-b'}),/mapping/);
  assert.throws(()=>fromAndroidEnvelope({...android,kind:'CHILD_SETTINGS',payload:{daily_target:10}},mapping),/remain local/);
  assert.throws(()=>fromAndroidEnvelope({...android,family_id:'forged'},mapping),/Unexpected Android envelope/);
  assert.throws(()=>fromAndroidEnvelope({...android,payload:{...android.payload,approvedReward:true}},mapping),/Unexpected learning evidence/);
});

test('word-use self-assessment preserves its separate skill without gaining parent authority', () => {
  const word = {...android,payload:{...android.payload,kind:'WORD',source:'SELF',skill:'WORD'}};
  const result=fromAndroidEnvelope(word,mapping);
  assert.equal(result.skill,'WORD');
  assert.equal(result.kind,'LEARNING_EVIDENCE');
  assert.throws(()=>fromAndroidEnvelope({...word,payload:{...word.payload,source:'QUIZ'}},mapping),/mismatch/);
  assert.throws(()=>fromAndroidEnvelope({...word,payload:{...word.payload,source:'PARENT'}},mapping),/PARENT/);
});
