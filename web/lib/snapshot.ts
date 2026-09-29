export const MAX_SNAPSHOT_BYTES = 1_000_000;

export interface WeekDay {
  date: string;
  completed: boolean;
  new_count: number;
  cursor: number;
}
export interface ChildSnapshot {
  id: string;
  display_name: string;
  age_group: string;
  daily_target: number;
  review_only: boolean;
  completed_today: boolean;
  completed_steps: number;
  total_steps: number;
  introduced: number;
  reviewing: number;
  mastered: number;
  due_reviews: number;
  daily_stamps: number;
  week: WeekDay[];
}
export interface ParentSnapshot {
  product: 'China Quest';
  schema_version: 1;
  snapshot_id: string;
  exported_at: string;
  day: string;
  source: 'ANDROID_PARENT_EXPORT';
  children: ChildSnapshot[];
}

export class SnapshotValidationError extends Error {
  constructor(message: string) { super(message); this.name = 'SnapshotValidationError'; }
}
function invalid(message: string): never { throw new SnapshotValidationError(message); }
function object(value: unknown, at: string): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) invalid(`${at} 应为对象 / must be an object`);
  return value as Record<string, unknown>;
}
function exactKeys(value: Record<string, unknown>, keys: string[], at: string) {
  const expected = [...keys].sort().join(',');
  if (Object.keys(value).sort().join(',') !== expected) invalid(`${at} 字段不符合版本 1 / unexpected fields`);
}
function integer(value: unknown, min: number, max: number, at: string): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < min || value > max) invalid(`${at} 数值无效 / invalid number`);
  return value as number;
}
function bool(value: unknown, at: string): boolean {
  if (typeof value !== 'boolean') invalid(`${at} 应为布尔值 / invalid boolean`);
  return value as boolean;
}
function string(value: unknown, min: number, max: number, at: string): string {
  if (typeof value !== 'string' || value.trim().length < min || value.length > max || /[\u0000-\u001f]/.test(value)) invalid(`${at} 文本无效 / invalid text`);
  return value as string;
}
function date(value: unknown, at: string): string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) invalid(`${at} 日期无效 / invalid date`);
  const parsed = new Date(`${value}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) invalid(`${at} 日期无效 / invalid date`);
  return value as string;
}
function addDays(day: string, days: number): string {
  const d = new Date(`${day}T00:00:00.000Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ROOT_KEYS = ['product','schema_version','snapshot_id','exported_at','day','source','children'];
const CHILD_KEYS = ['id','display_name','age_group','daily_target','review_only','completed_today','completed_steps',
  'total_steps','introduced','reviewing','mastered','due_reviews','daily_stamps','week'];
const WEEK_KEYS = ['date','completed','new_count','cursor'];

export function validateSnapshot(raw: unknown): ParentSnapshot {
  const root = object(raw, '报告');
  exactKeys(root, ROOT_KEYS, '报告');
  if (root.product !== 'China Quest' || root.schema_version !== 1 || root.source !== 'ANDROID_PARENT_EXPORT') {
    invalid('报告来源或版本不受支持 / unsupported report source or version');
  }
  if (typeof root.snapshot_id !== 'string' || !UUID.test(root.snapshot_id)) invalid('报告 ID 无效 / invalid snapshot ID');
  if (typeof root.exported_at !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(root.exported_at) ||
      Number.isNaN(Date.parse(root.exported_at))) invalid('导出时间无效 / invalid export time');
  const reportDay = date(root.day, '报告日期');
  if (!Array.isArray(root.children) || root.children.length < 1 || root.children.length > 20) invalid('孩子列表无效 / invalid children');
  const seen = new Set<string>();
  for (const [index, item] of root.children.entries()) {
    const child = object(item, `孩子 ${index + 1}`);
    exactKeys(child, CHILD_KEYS, `孩子 ${index + 1}`);
    const id = string(child.id, 1, 64, '孩子 ID');
    if (!/^[A-Za-z0-9_-]+$/.test(id) || seen.has(id)) invalid('孩子 ID 重复或无效 / duplicate or invalid child ID');
    seen.add(id);
    string(child.display_name, 1, 80, '孩子称呼');
    string(child.age_group, 1, 40, '年龄组');
    integer(child.daily_target, 0, 10, '每日目标');
    bool(child.review_only, '只复习');
    bool(child.completed_today, '今天完成');
    const steps = integer(child.completed_steps, 0, 100_000, '已完成步骤');
    const total = integer(child.total_steps, 0, 100_000, '总步骤');
    if (steps > total) invalid('已完成步骤超过总步骤 / invalid progress');
    const introduced = integer(child.introduced, 0, 1_000_000, '已接触');
    const reviewing = integer(child.reviewing, 0, 1_000_000, '学习复习中');
    const mastered = integer(child.mastered, 0, 1_000_000, '稳定掌握');
    const due = integer(child.due_reviews, 0, 1_000_000, '待复习');
    const stamps = integer(child.daily_stamps, 0, 1_000_000, '印章');
    if (reviewing + mastered > introduced || due > introduced) invalid('学习计数不一致 / inconsistent learning counts');
    if (!Array.isArray(child.week) || child.week.length !== 7) invalid('近七天必须有 7 日 / week must have seven days');
    let completedDays = 0;
    let newThisWeek = 0;
    for (let offset = 0; offset < 7; offset++) {
      const day = object(child.week[offset], `第 ${offset + 1} 天`);
      exactKeys(day, WEEK_KEYS, `第 ${offset + 1} 天`);
      if (date(day.date, '学习日期') !== addDays(reportDay, offset - 6)) invalid('近七天日期不连续 / week dates are not consecutive');
      if (bool(day.completed, '当日完成')) completedDays++;
      newThisWeek += integer(day.new_count, 0, 10_000, '当日新字');
      integer(day.cursor, 0, 100_000, '当日进度');
      if (day.completed && day.cursor === 0) invalid('完成日缺少学习步骤 / completed day has no steps');
    }
    if (newThisWeek > introduced || completedDays > stamps) invalid('近七天计数不一致 / inconsistent week');
    if (child.completed_today !== child.week[6].completed || steps !== child.week[6].cursor ||
        (child.completed_today && (total === 0 || steps !== total))) invalid('今日进度与日历不一致 / today does not match week');
  }
  return raw as ParentSnapshot;
}

export function parseSnapshotText(text: string): ParentSnapshot {
  if (new TextEncoder().encode(text).byteLength >= MAX_SNAPSHOT_BYTES) invalid('文件需小于 1 MB / file must be under 1 MB');
  let parsed: unknown;
  try { parsed = JSON.parse(text); } catch { invalid('文件不是有效 JSON / invalid JSON'); }
  return validateSnapshot(parsed);
}

export async function parseSnapshotFile(file: File): Promise<ParentSnapshot> {
  if (file.size >= MAX_SNAPSHOT_BYTES) invalid('文件需小于 1 MB / file must be under 1 MB');
  const bytes = await file.arrayBuffer();
  let text: string;
  try { text = new TextDecoder('utf-8', { fatal: true }).decode(bytes); }
  catch { invalid('文件不是 UTF-8 / invalid UTF-8'); }
  return parseSnapshotText(text);
}
