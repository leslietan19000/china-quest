-- Development schema for local PostgreSQL/PGlite validation. This is not a Supabase migration.
-- Apply only to a new, empty database. Family pairing and derived-state writes use a trusted server role.
-- Supabase supplies auth.uid(); the local tests install a compatible test shim before this file.

create table public.families (
  id uuid primary key,
  display_name text not null check (length(trim(display_name)) between 1 and 80),
  timezone text not null default 'UTC' check (length(timezone) between 1 and 80)
);
create table public.parents (
  user_id uuid not null,
  family_id uuid not null references public.families(id),
  primary key (user_id, family_id)
);
create index parents_family_user_idx on public.parents(family_id, user_id);
create table public.children (
  id uuid primary key,
  family_id uuid not null references public.families(id),
  display_name text not null check (length(trim(display_name)) between 1 and 80),
  age_group text not null default 'AGE_8_10' check (length(age_group) between 1 and 40),
  avatar text not null default 'explorer' check (length(avatar) between 1 and 80),
  learning_stage text not null default 'STAGE_1_CHARACTER' check (learning_stage in ('STAGE_1_CHARACTER','STAGE_2_WORD','STAGE_3_SENTENCE','STAGE_4_READING','STAGE_5_EXPRESSION','STAGE_6_CREATION')),
  daily_target integer not null default 5 check (daily_target between 0 and 10),
  review_only boolean not null default false,
  preferences jsonb not null default '{}'::jsonb check (jsonb_typeof(preferences) = 'object'),
  current_season text not null default 'preparing-china' check (length(current_season) between 1 and 80),
  current_stage integer not null default 0 check (current_stage >= 0),
  created_at timestamptz not null default now(),
  unique (family_id, id)
);
create table public.child_accounts (
  user_id uuid not null,
  family_id uuid not null,
  child_id uuid not null,
  primary key (user_id, child_id),
  foreign key (family_id, child_id) references public.children(family_id, id)
);
create index child_accounts_family_child_user_idx on public.child_accounts(family_id, child_id, user_id);

-- All membership lookups run with the caller's RLS. The membership policies below use only auth.uid().
create function public.is_parent(p_family_id uuid) returns boolean language sql stable security invoker
as $$ select exists (select 1 from public.parents p where p.family_id = p_family_id and p.user_id = (select auth.uid())) $$;
create function public.owns_child(p_family_id uuid, p_child_id uuid) returns boolean language sql stable security invoker
as $$ select exists (select 1 from public.child_accounts a where a.family_id = p_family_id and a.child_id = p_child_id and a.user_id = (select auth.uid())) $$;
create function public.is_child_family(p_family_id uuid) returns boolean language sql stable security invoker
as $$ select exists (select 1 from public.child_accounts a where a.family_id = p_family_id and a.user_id = (select auth.uid())) $$;

create table public.characters (
  id text primary key check (length(id) between 1 and 80),
  hanzi text not null,
  pinyin text not null,
  source_kind text not null check (source_kind in ('UNIHAN', 'CURATED')),
  source_version text not null,
  source_url text not null,
  content_hash text not null check (content_hash ~ '^[0-9a-f]{64}$'),
  approved_version_hash text,
  teaching_text text,
  check (approved_version_hash is null or approved_version_hash = content_hash)
);

-- Client input is typed and append only. PARENT evidence is generated from kind, never a client claim.
create table public.sync_events (
  event_id uuid primary key,
  family_id uuid not null,
  child_id uuid not null,
  actor_user_id uuid not null,
  kind text not null check (kind in ('LEARNING_EVIDENCE', 'QUIZ_ATTEMPT', 'PARENT_CHECK', 'DAILY_COMPLETE')),
  occurred_on date not null,
  character_id text references public.characters(id),
  skill text check (skill in ('RECOGNITION', 'PRONUNCIATION', 'MEANING', 'WRITING', 'WORD')),
  outcome text check (outcome in ('EXPOSURE', 'CORRECT', 'ALMOST', 'WRONG')),
  evidence_source text generated always as (
    case kind when 'PARENT_CHECK' then 'PARENT' when 'QUIZ_ATTEMPT' then 'QUIZ' else 'SELF' end
  ) stored,
  received_at timestamptz not null default now(),
  foreign key (family_id, child_id) references public.children(family_id, id),
  unique (family_id, child_id, event_id),
  check (
    (kind = 'DAILY_COMPLETE' and character_id is null and skill is null and outcome is null) or
    (kind = 'LEARNING_EVIDENCE' and character_id is not null and skill is not null and outcome is not null) or
    (kind in ('QUIZ_ATTEMPT', 'PARENT_CHECK') and character_id is not null and skill is not null and outcome in ('CORRECT','ALMOST','WRONG'))
  )
);
create index sync_events_family_child_day_idx on public.sync_events(family_id, child_id, occurred_on);
create index sync_events_actor_idx on public.sync_events(actor_user_id);

create table public.child_learning_profiles (
  family_id uuid not null,
  child_id uuid not null,
  stage text not null default 'STAGE_1_CHARACTER' check (stage in ('STAGE_1_CHARACTER','STAGE_2_WORD','STAGE_3_SENTENCE','STAGE_4_READING','STAGE_5_EXPRESSION','STAGE_6_CREATION')),
  character_level integer not null default 0 check (character_level between 0 and 100),
  vocabulary_level integer not null default 0 check (vocabulary_level between 0 and 100),
  reading_level integer not null default 0 check (reading_level between 0 and 100),
  writing_level integer not null default 0 check (writing_level between 0 and 100),
  pronunciation_level integer not null default 0 check (pronunciation_level between 0 and 100),
  expression_level integer not null default 0 check (expression_level between 0 and 100),
  real_world_usage_level integer not null default 0 check (real_world_usage_level between 0 and 100),
  creator_level integer not null default 0 check (creator_level between 0 and 100),
  builder_level integer not null default 0 check (builder_level between 0 and 100),
  primary key (family_id, child_id),
  foreign key (family_id, child_id) references public.children(family_id, id)
);
create table public.child_character_mastery (
  family_id uuid not null,
  child_id uuid not null,
  character_id text not null references public.characters(id),
  recognition integer not null default 0 check (recognition between 0 and 100),
  pronunciation integer not null default 0 check (pronunciation between 0 and 100),
  meaning integer not null default 0 check (meaning between 0 and 100),
  writing integer not null default 0 check (writing between 0 and 100),
  word integer not null default 0 check (word between 0 and 100),
  successful_days integer not null default 0 check (successful_days >= 0),
  last_success_date date,
  parent_checks integer not null default 0 check (parent_checks >= 0),
  due_on date not null,
  primary key (family_id, child_id, character_id),
  foreign key (family_id, child_id) references public.children(family_id, id)
);
create index child_character_mastery_due_idx on public.child_character_mastery(family_id, child_id, due_on);
create table public.learning_sessions (
  id uuid primary key,
  family_id uuid not null,
  child_id uuid not null,
  session_day date not null,
  cursor integer not null default 0 check (cursor >= 0),
  completed boolean not null default false,
  unique (family_id, child_id, session_day),
  foreign key (family_id, child_id) references public.children(family_id, id)
);
create index learning_sessions_family_child_idx on public.learning_sessions(family_id, child_id);
create table public.review_events (
  id uuid primary key,
  family_id uuid not null,
  child_id uuid not null,
  sync_event_id uuid not null unique,
  character_id text not null references public.characters(id),
  reviewed_on date not null,
  outcome text not null check (outcome in ('CORRECT','ALMOST','WRONG')),
  foreign key (family_id, child_id) references public.children(family_id, id),
  foreign key (family_id, child_id, sync_event_id) references public.sync_events(family_id, child_id, event_id)
);
create index review_events_family_child_day_idx on public.review_events(family_id, child_id, reviewed_on);
create table public.quiz_attempts (
  id uuid primary key,
  family_id uuid not null,
  child_id uuid not null,
  sync_event_id uuid not null unique,
  character_id text not null references public.characters(id),
  attempted_on date not null,
  correct boolean not null,
  foreign key (family_id, child_id) references public.children(family_id, id),
  foreign key (family_id, child_id, sync_event_id) references public.sync_events(family_id, child_id, event_id)
);
create index quiz_attempts_family_child_day_idx on public.quiz_attempts(family_id, child_id, attempted_on);
create table public.device_child_links (
  device_id uuid not null,
  family_id uuid not null,
  child_id uuid not null,
  paired_by_user_id uuid not null,
  primary key (device_id, child_id),
  foreign key (family_id, child_id) references public.children(family_id, id)
);
create index device_child_links_family_child_idx on public.device_child_links(family_id, child_id);
create table public.family_reward_budgets (
  family_id uuid primary key references public.families(id),
  weekly_choice_target_days integer not null default 5 check (weekly_choice_target_days between 1 and 7),
  max_weekly_choices integer not null default 1 check (max_weekly_choices between 0 and 7)
);

-- RLS on every exposed public table. Only trusted provisioning can create memberships/derived data.
alter table public.families enable row level security;
alter table public.parents enable row level security;
alter table public.children enable row level security;
alter table public.child_accounts enable row level security;
alter table public.characters enable row level security;
alter table public.sync_events enable row level security;
alter table public.child_learning_profiles enable row level security;
alter table public.child_character_mastery enable row level security;
alter table public.learning_sessions enable row level security;
alter table public.review_events enable row level security;
alter table public.quiz_attempts enable row level security;
alter table public.device_child_links enable row level security;
alter table public.family_reward_budgets enable row level security;

create policy parents_self on public.parents for select to authenticated using (user_id = (select auth.uid()));
create policy child_accounts_self on public.child_accounts for select to authenticated using (user_id = (select auth.uid()));
create policy families_parent on public.families for select to authenticated using (public.is_parent(id));
create policy children_member on public.children for select to authenticated using (public.is_parent(family_id) or public.owns_child(family_id,id));
create policy children_parent_update on public.children for update to authenticated
  using (public.is_parent(family_id)) with check (public.is_parent(family_id));
create policy characters_visible on public.characters for select to authenticated
  using (approved_version_hash = content_hash or exists (select 1 from public.parents p where p.user_id = (select auth.uid())));
create policy sync_events_member_select on public.sync_events for select to authenticated
  using (public.is_parent(family_id) or public.owns_child(family_id,child_id));
create policy sync_events_member_insert on public.sync_events for insert to authenticated
  with check (actor_user_id = (select auth.uid()) and (
    (kind in ('LEARNING_EVIDENCE','QUIZ_ATTEMPT','DAILY_COMPLETE') and (public.is_parent(family_id) or public.owns_child(family_id,child_id))) or
    (kind = 'PARENT_CHECK' and public.is_parent(family_id))
  ));
create policy child_learning_profiles_member on public.child_learning_profiles for select to authenticated
  using (public.is_parent(family_id) or public.owns_child(family_id,child_id));
create policy child_character_mastery_member on public.child_character_mastery for select to authenticated
  using (public.is_parent(family_id) or public.owns_child(family_id,child_id));
create policy learning_sessions_member on public.learning_sessions for select to authenticated
  using (public.is_parent(family_id) or public.owns_child(family_id,child_id));
create policy review_events_member on public.review_events for select to authenticated
  using (public.is_parent(family_id) or public.owns_child(family_id,child_id));
create policy quiz_attempts_member on public.quiz_attempts for select to authenticated
  using (public.is_parent(family_id) or public.owns_child(family_id,child_id));
create policy device_child_links_member on public.device_child_links for select to authenticated
  using (public.is_parent(family_id) or public.owns_child(family_id,child_id));
create policy family_reward_budgets_parent on public.family_reward_budgets for select to authenticated
  using (public.is_parent(family_id));
create policy family_reward_budgets_parent_update on public.family_reward_budgets for update to authenticated
  using (public.is_parent(family_id)) with check (public.is_parent(family_id));

-- Ingestion is a same-role RPC: RLS still applies and a UUID replay must match every typed field.
create function public.ingest_sync_event(
  p_event_id uuid, p_family_id uuid, p_child_id uuid, p_kind text, p_occurred_on date,
  p_character_id text, p_skill text, p_outcome text
) returns text language plpgsql security invoker set search_path = public, pg_temp as $$
declare existing public.sync_events%rowtype; inserted integer;
begin
  if (select auth.uid()) is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  insert into public.sync_events(event_id,family_id,child_id,actor_user_id,kind,occurred_on,character_id,skill,outcome)
  values(p_event_id,p_family_id,p_child_id,(select auth.uid()),p_kind,p_occurred_on,p_character_id,p_skill,p_outcome)
  on conflict (event_id) do nothing;
  get diagnostics inserted = row_count;
  if inserted = 1 then return 'accepted'; end if;
  select * into existing from public.sync_events where event_id = p_event_id;
  if not found then raise exception 'SYNC_EVENT_ID_UNAVAILABLE' using errcode = '23505'; end if;
  if existing.family_id is distinct from p_family_id or existing.child_id is distinct from p_child_id or
     existing.actor_user_id is distinct from (select auth.uid()) or existing.kind is distinct from p_kind or
     existing.occurred_on is distinct from p_occurred_on or existing.character_id is distinct from p_character_id or
     existing.skill is distinct from p_skill or existing.outcome is distinct from p_outcome then
    raise exception 'SYNC_EVENT_CONFLICT' using errcode = '23505';
  end if;
  return 'duplicate';
end $$;

revoke all on all tables in schema public from anon, authenticated;
revoke all on all functions in schema public from public, anon, authenticated;
grant usage on schema public to authenticated;
grant execute on function public.is_parent(uuid), public.owns_child(uuid,uuid), public.is_child_family(uuid),
  public.ingest_sync_event(uuid,uuid,uuid,text,date,text,text,text) to authenticated;
grant select on public.families, public.parents, public.children, public.child_accounts, public.characters,
  public.sync_events, public.child_learning_profiles, public.child_character_mastery, public.learning_sessions,
  public.review_events, public.quiz_attempts, public.device_child_links, public.family_reward_budgets to authenticated;
grant update (display_name,daily_target,review_only) on public.children to authenticated;
grant insert on public.sync_events to authenticated;
grant update (weekly_choice_target_days,max_weekly_choices) on public.family_reward_budgets to authenticated;
