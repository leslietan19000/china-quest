/** The transport accepts only these typed fields. Identity and PARENT evidence come from auth/RLS. */
export type SyncKind = 'LEARNING_EVIDENCE' | 'QUIZ_ATTEMPT' | 'PARENT_CHECK' | 'DAILY_COMPLETE';
export type Skill = 'RECOGNITION' | 'PRONUNCIATION' | 'MEANING' | 'WRITING' | 'WORD';
export type Outcome = 'EXPOSURE' | 'CORRECT' | 'ALMOST' | 'WRONG';
export interface SyncEventInput {
  eventId: string;
  familyId: string;
  childId: string;
  kind: SyncKind;
  occurredOn: string;
  characterId: string | null;
  skill: Skill | null;
  outcome: Outcome | null;
}
export interface TrustedLocalChildMapping {
  localChildId: string;
  familyId: string;
  serverChildId: string;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const KINDS = new Set<SyncKind>(['LEARNING_EVIDENCE','QUIZ_ATTEMPT','PARENT_CHECK','DAILY_COMPLETE']);
const SKILLS = new Set<Skill>(['RECOGNITION','PRONUNCIATION','MEANING','WRITING','WORD']);
const OUTCOMES = new Set<Outcome>(['EXPOSURE','CORRECT','ALMOST','WRONG']);
const KEYS = ['eventId','familyId','childId','kind','occurredOn','characterId','skill','outcome'];

export function validateSyncEvent(raw: unknown): SyncEventInput {
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Expected sync event object');
  const value = raw as Record<string, unknown>;
  if (Object.keys(value).sort().join(',') !== [...KEYS].sort().join(',')) throw new Error('Unexpected sync event fields');
  for (const key of ['eventId','familyId','childId']) {
    if (typeof value[key] !== 'string' || !UUID.test(value[key])) throw new Error(`Invalid ${key}`);
  }
  if (!KINDS.has(value.kind as SyncKind)) throw new Error('Invalid kind');
  if (typeof value.occurredOn !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value.occurredOn) ||
      Number.isNaN(Date.parse(`${value.occurredOn}T00:00:00Z`)) ||
      new Date(`${value.occurredOn}T00:00:00Z`).toISOString().slice(0,10) !== value.occurredOn) throw new Error('Invalid date');
  if (value.kind === 'DAILY_COMPLETE') {
    if (value.characterId !== null || value.skill !== null || value.outcome !== null) throw new Error('Daily completion has no assessment');
  } else {
    if (typeof value.characterId !== 'string' || value.characterId.length < 1 || value.characterId.length > 80) throw new Error('Invalid characterId');
    if (!SKILLS.has(value.skill as Skill) || !OUTCOMES.has(value.outcome as Outcome)) throw new Error('Invalid assessment');
    if (value.kind !== 'LEARNING_EVIDENCE' && value.outcome === 'EXPOSURE') throw new Error('Exposure is a learning event');
  }
  return value as unknown as SyncEventInput;
}

/** Translate the Android SQLite outbox format after the device has been paired by a trusted flow. */
export function fromAndroidEnvelope(raw: unknown, mapping: TrustedLocalChildMapping): SyncEventInput {
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Expected Android envelope');
  const envelope = raw as Record<string, unknown>;
  const allowed = ['id','child_id','kind','day','payload','created_at','schema_version'];
  if (Object.keys(envelope).sort().join(',') !== allowed.sort().join(',')) throw new Error('Unexpected Android envelope fields');
  if (envelope.schema_version !== 1) throw new Error('Unsupported Android schema');
  if (envelope.child_id !== mapping.localChildId) throw new Error('Child mapping mismatch');
  if (typeof envelope.created_at !== 'string' || Number.isNaN(Date.parse(envelope.created_at))) throw new Error('Invalid created_at');
  if (envelope.payload === null || typeof envelope.payload !== 'object' || Array.isArray(envelope.payload)) throw new Error('Invalid payload');
  const payload = envelope.payload as Record<string, unknown>;
  if (envelope.kind === 'CHILD_SETTINGS') throw new Error('Child settings remain local until parent settings transport exists');
  if (envelope.kind === 'DAILY_COMPLETE') {
    if (Object.keys(payload).sort().join(',') !== 'task_count') throw new Error('Unexpected completion fields');
    if (typeof payload.task_count !== 'number' || !Number.isInteger(payload.task_count) || payload.task_count < 1) throw new Error('Invalid completion');
    return validateSyncEvent({eventId:envelope.id,familyId:mapping.familyId,childId:mapping.serverChildId,
      kind:'DAILY_COMPLETE',occurredOn:envelope.day,characterId:null,skill:null,outcome:null});
  }
  if (envelope.kind !== 'LEARNING_EVIDENCE') throw new Error('Unsupported Android event kind');
  const learningKeys = ['kind','character_id','cursor','outcome','skill','source','effective_date','session_date','algorithm_version'];
  if (Object.keys(payload).sort().join(',') !== learningKeys.sort().join(',') ||
      typeof payload.cursor !== 'number' || !Number.isInteger(payload.cursor) || payload.cursor < 0) {
    throw new Error('Unexpected learning evidence fields');
  }
  if (payload.source === 'PARENT') throw new Error('Android learning evidence cannot claim PARENT');
  if (payload.session_date !== envelope.day || payload.algorithm_version !== 1) throw new Error('Invalid learning event version or day');
  const task = payload.kind;
  const expected = task === 'LEARN' ? ['SELF','RECOGNITION','EXPOSURE'] :
    task === 'FIND' || task === 'REVIEW' ? ['QUIZ','RECOGNITION',null] :
    task === 'READ' ? ['SELF','PRONUNCIATION',null] :
    task === 'WORD' ? ['SELF','WORD',null] :
    task === 'WRITE' ? ['SELF','WRITING',null] : null;
  if (expected === null || payload.source !== expected[0] || payload.skill !== expected[1] ||
      (expected[2] === 'EXPOSURE' ? payload.outcome !== 'EXPOSURE' : payload.outcome === 'EXPOSURE')) {
    throw new Error('Task, source, skill or outcome mismatch');
  }
  return validateSyncEvent({eventId:envelope.id,familyId:mapping.familyId,childId:mapping.serverChildId,
    kind:payload.source === 'QUIZ' ? 'QUIZ_ATTEMPT' : 'LEARNING_EVIDENCE',
    occurredOn:payload.effective_date,characterId:payload.character_id,skill:payload.skill,outcome:payload.outcome});
}

/** Pass only validated values to the same-role Postgres RPC; never send actor, evidence source, rewards or derived mastery. */
export function toIngestArgs(input: SyncEventInput) {
  return {
    p_event_id: input.eventId,
    p_family_id: input.familyId,
    p_child_id: input.childId,
    p_kind: input.kind,
    p_occurred_on: input.occurredOn,
    p_character_id: input.characterId,
    p_skill: input.skill,
    p_outcome: input.outcome,
  };
}
