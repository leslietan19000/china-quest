'use client';

import { useRef, useState, type ChangeEvent, type ReactNode } from 'react';
import demoReport from '../public/demo-report.json';
import { parseSnapshotFile, validateSnapshot, type ChildSnapshot, type ParentSnapshot } from '../lib/snapshot';

type View = 'overview' | 'child' | 'guide';
type Origin = 'imported' | 'demo' | null;

function Emblem({ size = 36 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true">
    <circle cx="24" cy="24" r="21" stroke="currentColor" strokeWidth="1.5" />
    <path d="M24 6v36M6 24h36M13 13l22 22M35 13 13 35" stroke="currentColor" strokeWidth=".8" opacity=".38" />
    <path d="m24 12 4.1 11.9L24 36l-4.1-12.1L24 12Z" fill="currentColor" />
    <circle cx="24" cy="24" r="2.4" fill="#f5f0e4" />
  </svg>;
}
function Arrow({ diagonal = false }: { diagonal?: boolean }) {
  return <svg width="17" height="17" viewBox="0 0 20 20" fill="none" aria-hidden="true">
    <path d={diagonal ? 'M4 15 15 4M7 4h8v8' : 'M3 10h13m-5-5 5 5-5 5'} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
  </svg>;
}
const nav: { id: View; zh: string; es: string; index: string }[] = [
  { id: 'overview', zh: '家庭总览', es: 'Panorama', index: '01' },
  { id: 'child', zh: '孩子旅程', es: 'Cada viaje', index: '02' },
  { id: 'guide', zh: '内容说明', es: 'Guía', index: '03' },
];
function weekDays(child: ChildSnapshot) { return child.week.filter(day => day.completed).length; }
function newThisWeek(child: ChildSnapshot) { return child.week.reduce((sum, day) => sum + day.new_count, 0); }
function shortDay(iso: string) { return `${Number(iso.slice(5, 7))}/${Number(iso.slice(8, 10))}`; }
function SectionLabel({ children }: { children: ReactNode }) { return <div className="section-label">{children}</div>; }

function WeekStrip({ child }: { child: ChildSnapshot }) {
  return <div className="week-strip" aria-label={`${child.display_name}近七天学习记录`}>
    {child.week.map((day, index) => <div className="week-cell" key={day.date}>
      <span className="week-date">{index === 6 ? '记录日' : shortDay(day.date)}</span>
      <span className={`week-mark ${day.completed ? 'done' : day.cursor > 0 ? 'partial' : ''}`} title={`${day.date}：${day.completed ? '已完成' : day.cursor > 0 ? '已有步骤，未完成' : '未学习'}`} aria-label={`${day.date} ${day.completed ? '已完成' : day.cursor > 0 ? '已有步骤，未完成' : '未学习'}`}>
        {day.completed ? '✓' : day.cursor > 0 ? '·' : ''}
      </span>
    </div>)}
  </div>;
}

function ChildCard({ child, onOpen }: { child: ChildSnapshot; onOpen: () => void }) {
  return <article className="child-card">
    <div className="child-card-top"><div className="avatar" aria-hidden="true">{child.display_name.slice(0, 1)}</div>
      <div><span className="eyebrow">独立的学习旅程 · Viaje propio</span><h3>{child.display_name}</h3></div>
      <span className={`status ${child.completed_today ? 'finished' : child.completed_steps > 0 ? 'started' : ''}`}>
        {child.completed_today ? '当日完成' : child.completed_steps > 0 ? '记录日进行中' : '记录日未开始'}
      </span>
    </div>
    <div className="metrics-grid">
      <Metric value={weekDays(child)} label="近 7 天完成学习" sub="días completados" />
      <Metric value={newThisWeek(child)} label="近 7 天新接触" sub="caracteres nuevos" />
      <Metric value={child.mastered} label="稳定掌握" sub="dominio estable" />
      <Metric value={child.due_reviews} label="待复习" sub="por repasar" accent />
    </div>
    <WeekStrip child={child} />
    <button className="text-action" type="button" onClick={onOpen}>查看这段旅程 <Arrow /></button>
  </article>;
}
function Metric({ value, label, sub, accent = false }: { value: number; label: string; sub: string; accent?: boolean }) {
  return <div className="metric"><strong className={accent ? 'accent' : ''}>{value}</strong><span>{label}</span><small>{sub}</small></div>;
}

export default function Dashboard() {
  const fileInput = useRef<HTMLInputElement>(null);
  const [snapshot, setSnapshot] = useState<ParentSnapshot | null>(null);
  const [origin, setOrigin] = useState<Origin>(null);
  const [view, setView] = useState<View>('overview');
  const [childId, setChildId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const selectedChild = snapshot?.children.find(child => child.id === childId) ?? snapshot?.children[0];

  function loadDemo() {
    const data = validateSnapshot(demoReport);
    setSnapshot(data); setOrigin('demo'); setChildId(data.children[0].id); setView('overview'); setError(null);
  }
  async function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setSnapshot(null); setOrigin(null); setError(null);
    try {
      const data = await parseSnapshotFile(file);
      setSnapshot(data); setOrigin('imported'); setChildId(data.children[0].id); setView('overview');
    } catch (cause) { setError(cause instanceof Error ? cause.message : '无法读取文件 / Could not read file'); }
  }
  function clear() { setSnapshot(null); setOrigin(null); setChildId(null); setView('overview'); setError(null); }
  function openChild(id: string) { setChildId(id); setView('child'); }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-symbol"><Emblem /></span><div><strong>CHINA QUEST</strong><small>我的中国远征 · Misión China</small></div></div>
      <div className="sidebar-rule" />
      <div className="nav-caption">FAMILY COMPANION · 家长空间</div>
      <nav className="navigation" aria-label="看板页面">
        {nav.map(item => <button key={item.id} type="button" className={`nav-item ${view === item.id ? 'active' : ''}`} onClick={() => setView(item.id)}>
          <span className="nav-index">{item.index}</span><span><b>{item.zh}</b><small>{item.es}</small></span><Arrow /></button>)}
      </nav>
      <div className="sidebar-bottom"><div className="dot" /><p>一段陪伴，一次探索。<br /><span>Un paso a la vez.</span></p></div>
    </aside>

    <main className="main">
      <div className="topline"><span>家长看板 <span className="separator">/</span> PANEL FAMILIAR</span><span className="topline-local">本地文件 · Solo en este navegador</span></div>
      {origin === 'demo' && <div className="demo-ribbon" role="status">匿名演示数据 · DATOS DE DEMOSTRACIÓN — 不是真实学习进度</div>}
      <header className="page-header"><div><div className="kicker">A QUIETER WAY TO SEE PROGRESS</div><h1>{view === 'overview' ? '看见每一步的成长' : view === 'child' ? '每个孩子自己的旅程' : '一起读懂学习记录'}</h1>
        <p>{view === 'overview' ? '一份从 BOOX 导出的静态记录，陪你了解记录日与之前六天。' : view === 'child' ? '关注自己的节奏，慢慢积累。' : '这些数字从哪里来，又能告诉我们什么。'}</p></div>
        <div className="header-stamp" aria-hidden="true"><Emblem size={66} /></div>
      </header>

      <input ref={fileInput} className="visually-hidden" type="file" accept=".json,application/json" onChange={onFile} aria-label="导入家长导出 JSON 文件" />
      {!snapshot ? <section className="empty-layout">
        <div className="empty-main"><SectionLabel>01 / 开始 · EMPEZAR</SectionLabel><h2>从孩子的设备，<br />带来一份当下的记录。</h2>
          <p>在 BOOX 上进入家长 PIN 区导出 JSON 文件，再在这里打开。文件只在这个浏览器标签页的内存中读取；关闭或刷新后需要重新导入。</p>
          <div className="actions"><button className="primary-button" type="button" onClick={() => fileInput.current?.click()}>选择导出文件 <Arrow /></button>
            <button className="secondary-button" type="button" onClick={loadDemo}>查看匿名演示</button></div>
          {error && <div className="error" role="alert">{error}</div>}
          <div className="privacy-note"><span className="privacy-icon">○</span><span>只读 · 无上传 · 不登录<br /><small>Solo lectura · Sin subir archivos</small></span></div>
        </div>
        <div className="steps-panel"><SectionLabel>导入步骤 / CÓMO ABRIR</SectionLabel>
          <div className="step"><span>01</span><p>在 BOOX 打开家长空间，输入家长 PIN。</p></div>
          <div className="step"><span>02</span><p>选择“导出家长报告”，保存 JSON 文件。</p></div>
          <div className="step"><span>03</span><p>回到这里，选择文件查看快照。</p></div>
          <div className="panel-foot">文件须小于 1 MB，且符合 China Quest 导出版本 1。</div>
        </div>
      </section> : <>
        <div className="snapshot-bar"><div><span className={`source-pill ${origin === 'demo' ? 'demo' : ''}`}>{origin === 'demo' ? '演示快照' : '已导入 · 只读'}</span>
          <strong>数据日期 {snapshot.day}</strong><span>导出于 {new Date(snapshot.exported_at).toLocaleString('zh-CN')} · 不是实时同步</span></div>
          <div className="snapshot-actions"><button type="button" onClick={() => fileInput.current?.click()}>更换文件</button><button type="button" onClick={clear}>清空</button></div></div>
        {error && <div className="error" role="alert">{error}</div>}
        {view === 'overview' && <section className="content-section"><div className="section-heading"><div><SectionLabel>01 / 家庭总览 · PANORAMA</SectionLabel><h2>各自前行，一起看见。</h2></div><p>这份记录包含 {snapshot.children.length} 位孩子。每张卡片只呈现自己的进度。</p></div>
          <div className="child-grid">{snapshot.children.map(child => <ChildCard child={child} key={child.id} onOpen={() => openChild(child.id)} />)}</div>
          <div className="quiet-note"><Emblem size={28} /><p>这些是导出当时的数字。新的学习会先保存在 BOOX 上，重新导出后才能在这里看到。</p></div>
        </section>}
        {view === 'child' && selectedChild && <section className="content-section"><div className="section-heading"><div><SectionLabel>02 / 孩子旅程 · CADA VIAJE</SectionLabel><h2>{selectedChild.display_name} 的一周</h2></div><p>单独查看，不作兄弟姐妹排名。</p></div>
          <div className="child-tabs" role="group" aria-label="选择孩子">{snapshot.children.map(child => <button key={child.id} type="button" className={child.id === selectedChild.id ? 'selected' : ''} onClick={() => setChildId(child.id)}>{child.display_name}</button>)}</div>
          <div className="detail-grid"><div className="detail-primary"><div className="detail-intro"><span className="eyebrow">近 7 天 · ÚLTIMOS SIETE DÍAS</span><strong>{weekDays(selectedChild)} <small>/ 7 天完成</small></strong><p>完成的日子被标记；已有步骤但未完成的日子单独显示。</p></div><WeekStrip child={selectedChild} />
            <div className="day-list">{selectedChild.week.map(day => <div key={day.date}><span>{day.date}</span><strong>{day.completed ? '已完成' : day.cursor > 0 ? `未完成 · 已有 ${day.cursor} 步` : '休息 / 未开始'}</strong><small>新接触 {day.new_count}</small></div>)}</div></div>
            <div className="detail-side"><SectionLabel>学习状态 / ESTADO</SectionLabel><div className="detail-stat"><span>近 7 天新接触</span><strong>{newThisWeek(selectedChild)}</strong></div><div className="detail-stat"><span>截至记录日已接触</span><strong>{selectedChild.introduced}</strong></div><div className="detail-stat"><span>学习／复习中</span><strong>{selectedChild.reviewing}</strong></div><div className="detail-stat"><span>稳定掌握</span><strong>{selectedChild.mastered}</strong></div><div className="detail-stat red"><span>记录日待复习</span><strong>{selectedChild.due_reviews}</strong></div>
              <div className="settings-note"><b>调整学习节奏</b><p>每日新字目标 {selectedChild.daily_target} · {selectedChild.review_only ? '只复习' : '新字＋复习'}。请在 BOOX 家长 PIN 区更改，网页只读。</p></div></div></div>
        </section>}
        {view === 'guide' && <section className="content-section guide"><div className="section-heading"><div><SectionLabel>03 / 内容说明 · GUÍA</SectionLabel><h2>数字背后的学习。</h2></div><p>一份说明，方便家长一起阅读，而不替孩子下结论。</p></div>
          <div className="guide-grid"><article><span className="guide-num">01</span><h3>已接触 · Conocidos</h3><p>孩子在设备上看过并练习过的字。近七天“新接触”取自每天首次出现的记录。</p></article><article><span className="guide-num">02</span><h3>待复习 · Por repasar</h3><p>到了复习日期的字。答错会调整复习间隔，但已有学习不会被清空。</p></article><article><span className="guide-num">03</span><h3>稳定掌握 · Dominio</h3><p>需要多天、多种能力和家长测试证据。朗读和写字目前由孩子自评，需家长陪伴观察。</p></article></div>
          <div className="guide-bottom"><div><SectionLabel>内容来源 / FUENTES</SectionLabel><h3>字典事实与学习引导</h3><p>基础字形、拼音、部首和笔画来自已标记来源的字典事实。未经人工审核的中文／西语解释与例句不会作为已批准教学内容展示。</p></div><div><SectionLabel>以后再来 / MÁS ADELANTE</SectionLabel><h3>规划中的旅程</h3><p>奖励、赛季、创作与 Builder 属于后续规划。此看板只读取当前设备导出的学习快照。</p></div></div>
        </section>}
      </>}
      <footer><span>CHINA QUEST <i>·</i> 家长陪伴版</span><span>只读快照 · El archivo permanece en esta pestaña</span></footer>
    </main>
  </div>;
}
