import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Icon from '../../components/Icon';
import { initials } from '../../components/CandidateShell';
import { useToast } from '../../contexts/Toast';
import api, { errorText } from '../../services/api';
import { StatusChip, minutes } from './shared';

const EVENT_TEXT = {
  TAB_HIDDEN: 'Switched tab', WINDOW_BLUR: 'Left the window', FULLSCREEN_EXIT: 'Left full screen',
  NEW_WINDOW: 'Opened in a new window', PASTE_BLOCKED: 'Paste blocked', COPY_BLOCKED: 'Copy blocked',
  CUT_BLOCKED: 'Cut blocked', RIGHT_CLICK: 'Right-click blocked', DROP_BLOCKED: 'Drop blocked',
  DEVTOOLS_KEY: 'Developer tools key', PERMISSION_PROMPT: 'Microphone prompt (not counted)',
  PRINT_SCREEN: 'Print Screen pressed', SHORTCUT_BLOCKED: 'Shortcut blocked',
};
const PART = {
  TEAMS: { label: 'Teams message', icon: 'chat' },
  EMAIL: { label: 'Email', icon: 'mail' },
  MEETING: { label: 'Meeting', icon: 'meeting' },
};

/** A point's score in words, so a reader does not have to interpret "6/10". */
const level = (s) => {
  if (s == null) return { text: 'Not marked', cls: 'lv-none' };
  if (s >= 9) return { text: 'Excellent', cls: 'lv-5' };
  if (s >= 7) return { text: 'Good', cls: 'lv-4' };
  if (s >= 5) return { text: 'Partial', cls: 'lv-3' };
  if (s >= 2) return { text: 'Weak', cls: 'lv-2' };
  return { text: 'Missing', cls: 'lv-1' };
};
const isAlways = (name = '') => name.startsWith('Tone:') || name.startsWith('Reader satisfaction:');
const pct = (m, max) => (m == null || !max ? 0 : Math.round((m / max) * 100));

// ------------------------------------------------------------------ the response, as it was written

function TeamsView({ x, name }) {
  return (
    <div className="resp resp-teams">
      <div className="resp-app"><span className="app-ic teams"><Icon name="chat" size={14} /></span> Microsoft Teams</div>
      {x.content?.trim() ? (
        <div className="teams-msg">
          <div className="avatar">{initials(name)}</div>
          <div><b>{name}</b><p>{x.content}</p></div>
        </div>
      ) : <div className="resp-empty">No reply was written.</div>}
    </div>
  );
}

function EmailView({ x, name, email }) {
  return (
    <div className="resp resp-email">
      <div className="resp-app"><span className="app-ic mail"><Icon name="mail" size={14} /></span> Email</div>
      <div className="mail-meta">
        <div><span>From</span>{name} &lt;{email}&gt;</div>
        <div><span>Subject</span><b>{x.subject || <i className="faint">(no subject)</i>}</b></div>
      </div>
      {x.content?.trim() ? <div className="mail-body">{x.content}</div> : <div className="resp-empty">No email was written.</div>}
    </div>
  );
}

function MeetingView({ x }) {
  return (
    <div className="resp resp-meeting">
      <div className="resp-app">
        <span className="app-ic mail"><Icon name="meeting" size={14} /></span> Live meeting transcript
        <span style={{ marginLeft: 'auto' }}>
          {x.meetingSatisfied === true && <span className="chip chip-ok">Participant satisfied</span>}
          {x.meetingSatisfied === false && <span className="chip chip-bad">Participant not satisfied</span>}
          {x.meetingSatisfied == null && x.transcript?.length > 0 && <span className="chip chip-warn">Ended by time</span>}
        </span>
      </div>
      <div className="meeting-transcript">
        {x.transcript?.length ? x.transcript.map((t, i) => (
          <div key={i} className={`bubble ${t.role === 'AI' ? 'them' : 'me'}`}>
            <small>{t.role === 'AI' ? 'Participant (AI)' : 'Candidate'}</small><p>{t.text}</p>
          </div>
        )) : <div className="resp-empty">The meeting was not started.</div>}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ one part: question, response, marking

function AnswerPanel({ x, who }) {
  const points = (x.metrics || []).filter((m) => !isAlways(m.name));
  const always = (x.metrics || []).filter((m) => isAlways(m.name));
  const p = pct(x.marks, x.maxMarks);
  return (
    <div className="answer-panel">
      <details className="card question-card" open>
        <summary>
          <span className="chip chip-blue">{x.code || PART[x.part].label}</span>
          <b>{x.title}</b>
          <span className="faint q-toggle">Question</span>
        </summary>
        {x.scenario && <div className="scenario">{x.scenario}</div>}
        <div><div className="task-label">Task given</div><div className="task">{x.task}</div></div>
      </details>

      <div className="review-grid">
        <div className="card review-left">
          <div className="review-title">Candidate's response</div>
          {x.part === 'TEAMS' && <TeamsView x={x} name={who.name} />}
          {x.part === 'EMAIL' && <EmailView x={x} name={who.name} email={who.email} />}
          {x.part === 'MEETING' && <MeetingView x={x} />}
          <div className="resp-stats">
            <span><b>{x.wordCount}</b> words</span>
            {x.part !== 'MEETING' && <span><b>{x.keystrokes}</b> edits</span>}
            {x.part !== 'MEETING' && <span className={x.pasteBlocked ? 'bad' : ''}><b>{x.pasteBlocked}</b> paste attempts</span>}
          </div>
        </div>

        <div className="card review-right">
          <div className="score-head">
            <div>
              <div className="review-title">Marks</div>
              {x.scoreStatus === 'SCORED' && <div className="big-mark">{x.marks}<small> / {Math.round(x.maxMarks)}</small></div>}
              {x.scoreStatus === 'PENDING' && <span className="chip chip-warn">Marking…</span>}
              {x.scoreStatus === 'FAILED' && <span className="chip chip-bad">Not marked</span>}
            </div>
            {x.scoreStatus === 'SCORED' && <div className={`pct-badge ${p >= 70 ? 'ok' : p >= 45 ? 'mid' : 'low'}`}>{p}%</div>}
          </div>
          {x.summary && <div className="ai-summary"><Icon name="flag" size={14} /> {x.summary}</div>}
          {x.scoreError && <div className="error-text">Marking failed: {x.scoreError}</div>}

          {points.length > 0 && (
            <div className="point-list">
              <div className="task-label">Points to cover <span className="faint">(from the question)</span></div>
              {points.map((m, i) => <PointRow key={i} m={m} />)}
            </div>
          )}
          {always.length > 0 && (
            <div className="point-list">
              <div className="task-label">Always checked</div>
              {always.map((m, i) => <PointRow key={i} m={{ ...m, name: m.name.split(':')[0] }} sub={m.name.split(':').slice(1).join(':').trim()} />)}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function PointRow({ m, sub }) {
  const lv = level(m.score);
  return (
    <div className="point">
      <div className="point-top">
        <span className="point-name">{m.name}{sub && <small>{sub}</small>}</span>
        <span className={`lv ${lv.cls}`}>{lv.text}<i>{m.score ?? '—'}/10</i></span>
      </div>
      {m.comment && <p className="point-why">{m.comment}</p>}
    </div>
  );
}

// ------------------------------------------------------------------ the page

export default function CandidateDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [order, setOrder] = useState(() => {
    try { return JSON.parse(sessionStorage.getItem('cfa_order') || '[]'); } catch { return []; }
  });
  const [d, setD] = useState(null);
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState(null);
  const [printing, setPrinting] = useState(false);
  const [clearing, setClearing] = useState(null);   // null | { reason } while the confirm box is open
  const [clearBusy, setClearBusy] = useState(false);

  const clearTest = async () => {
    setClearBusy(true);
    try {
      await api.post(`/admin/attempts/${id}/reset`, { reason: clearing.reason || null });
      toast(`${d.attempt.name}'s test was cleared. They can now take it again.`, 'ok');
      try { sessionStorage.removeItem('cfa_order'); } catch { /* private mode */ }
      navigate('/admin/candidates');
    } catch (e) {
      toast(errorText(e), 'bad');
      setClearBusy(false);
    }
  };

  const load = useCallback(() => api.get(`/admin/attempts/${id}`).then(({ data }) => {
    setD(data);
    setTab((t) => t || data.answers[0]?.part || 'ACTIVITY');
  }).catch((e) => toast(errorText(e), 'bad')), [id, toast]);
  useEffect(() => { setD(null); setTab(null); load(); }, [load]);

  // Opened directly (not from the list): step through every candidate who has taken the test.
  useEffect(() => {
    if (order.length) return;
    api.get('/admin/attempts').then(({ data }) => setOrder(data.filter((r) => r.attemptId).map((r) => r.attemptId))).catch(() => {});
  }, [order.length]);

  const pos = order.indexOf(Number(id));
  const prevId = pos > 0 ? order[pos - 1] : null;
  const nextId = pos >= 0 && pos < order.length - 1 ? order[pos + 1] : null;

  useEffect(() => {
    const onKey = (e) => {
      if (['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target.tagName)) return;
      if (e.key === 'ArrowLeft' && prevId) navigate(`/admin/candidates/${prevId}`);
      if (e.key === 'ArrowRight' && nextId) navigate(`/admin/candidates/${nextId}`);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [navigate, prevId, nextId]);

  const rescore = async (all) => {
    setBusy(true);
    try {
      const { data } = await api.post(`/admin/attempts/${id}/rescore`, null, { params: { all } });
      setD(data);
      toast('Marking finished.', 'ok');
    } catch (e) {
      toast(errorText(e), 'bad');
    } finally {
      setBusy(false);
    }
  };

  const print = () => {
    setPrinting(true);
    setTimeout(() => { window.print(); setPrinting(false); }, 300);
  };

  if (!d) return <div className="center-load"><div className="spinner" /></div>;
  const a = d.attempt;
  const failed = d.answers.some((x) => x.scoreStatus === 'FAILED');
  const counted = d.events.filter((e) => e.counted).length;
  const who = { name: a.name, email: a.email };
  const timing = (part) => d.timings.find((t) => t.part === part);

  const activity = (
    <div className="card activity">
      <div className="activity-head">
        <h3>Activity log</h3>
        <span className={`chip ${counted ? 'chip-bad' : 'chip-ok'}`}>{counted} of 3 strikes</span>
        <span className="faint" style={{ fontSize: 12.5 }}>{d.events.length} events recorded</span>
      </div>
      <div className="events">
        {d.events.map((e, i) => (
          <div key={i} className={`event ${e.counted ? 'counted' : ''}`}>
            <span className="pin" />
            <div>
              <b>{EVENT_TEXT[e.type] || e.type}{e.counted ? ` · strike ${e.violationNumber}` : ''}</b>
              <small>{e.at}{e.part ? ` · ${PART[e.part]?.label || e.part}` : ''}</small>
              {e.detail && <small>{e.detail}</small>}
            </div>
          </div>
        ))}
        {!d.events.length && <span className="faint" style={{ padding: '12px 0' }}>Nothing unusual was recorded.</span>}
      </div>
      <div className="hint" style={{ padding: '0 20px 18px' }}>Browser: {d.userAgent || '—'}<br />IP address: {d.ipAddress || '—'}</div>
      {d.resets?.length > 0 && (
        <div className="resets">
          <div className="task-label">Earlier tests cleared by an admin</div>
          {d.resets.map((r, i) => (
            <div key={i} className="reset-row">
              <b>{r.clearedAt}</b> by {r.clearedBy}
              <span className="faint"> &middot; was {r.previousStatus?.toLowerCase().replace('_', ' ')}{r.previousOverall != null ? `, ${Math.round(r.previousOverall)}/100` : ''}{r.previousEndReason ? ` (${r.previousEndReason})` : ''}</span>
              {r.reason && <div className="hint">Reason: {r.reason}</div>}
            </div>
          ))}
        </div>
      )}
    </div>
  );

  return (
    <>
      <div className="main-head no-print">
        <div>
          <Link to="/admin/candidates" className="muted" style={{ fontSize: 13, display: 'inline-flex', gap: 6, alignItems: 'center' }}>
            <Icon name="back" size={15} /> All candidates
          </Link>
          <h1>Candidate report</h1>
        </div>
        <div className="toolbar">
          {pos >= 0 && (
            <div className="pager" title="You can also use the left and right arrow keys">
              <button className="icon-btn" disabled={!prevId} onClick={() => navigate(`/admin/candidates/${prevId}`)} aria-label="Previous candidate"><Icon name="back" size={15} /></button>
              <span className="num">{pos + 1} of {order.length}</span>
              <button className="icon-btn" disabled={!nextId} onClick={() => navigate(`/admin/candidates/${nextId}`)} aria-label="Next candidate"><Icon name="arrow" size={15} /></button>
            </div>
          )}
          {a.status !== 'IN_PROGRESS' && failed && <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => rescore(false)}><Icon name="refresh" size={15} /> Mark unmarked answers</button>}
          {a.status !== 'IN_PROGRESS' && <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => rescore(true)}><Icon name="refresh" size={15} /> {busy ? 'Marking…' : 'Re-mark all'}</button>}
          <button className="btn btn-ghost btn-sm" onClick={print}><Icon name="download" size={15} /> Print / PDF</button>
          <button className="btn btn-sm btn-clear" onClick={() => setClearing({ reason: '' })}><Icon name="refresh" size={15} /> Clear test</button>
        </div>
      </div>

      <div className="main-body">
        <div className="card summary-card">
          <div className="avatar big-avatar">{initials(a.name)}</div>
          <div className="summary-who">
            <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
              <h2>{a.name}</h2><StatusChip status={a.status} />
            </div>
            <div className="facts">
              <span>ID <b>{a.employeeId}</b></span><span>Team <b>{a.team}</b></span><span>Role <b>{a.jobRole}</b></span><span>{a.email}</span>
            </div>
            <div className="facts">
              <span>Started <b>{a.startedAt || '—'}</b></span><span>Ended <b>{a.submittedAt || '—'}</b></span>
              <span>Time taken <b>{minutes(a.durationSeconds)}</b></span>{a.endReason && <span>How it ended <b>{a.endReason}</b></span>}
            </div>
          </div>
          <div className="summary-score">
            <div className="ring" style={{ '--p': a.totalMarks ?? 0 }}>
              <div><span><strong>{a.totalMarks ?? '—'}</strong><br /><small>of 100</small></span></div>
            </div>
          </div>
        </div>

        <div className="tabs no-print" role="tablist">
          {d.answers.map((x) => {
            const ps = a.parts.find((p) => p.part === x.part);
            return (
              <button key={x.part} role="tab" aria-selected={tab === x.part} className={`tab ${tab === x.part ? 'on' : ''}`} onClick={() => setTab(x.part)}>
                <Icon name={PART[x.part].icon} size={17} />
                <span className="tab-text"><b>{PART[x.part].label}</b><small>{timing(x.part)?.seconds != null ? minutes(timing(x.part).seconds) : 'Not reached'}</small></span>
                <span className="tab-mark">{ps?.marks ?? '—'}<small>/{Math.round(ps?.maxMarks || x.maxMarks)}</small></span>
              </button>
            );
          })}
          <button role="tab" aria-selected={tab === 'ACTIVITY'} className={`tab ${tab === 'ACTIVITY' ? 'on' : ''} ${counted ? 'warn' : ''}`} onClick={() => setTab('ACTIVITY')}>
            <Icon name="shield" size={17} />
            <span className="tab-text"><b>Activity log</b><small>{d.events.length} events</small></span>
            <span className="tab-mark">{counted}<small>/3</small></span>
          </button>
        </div>

        {clearing && (
          <div className="overlay" role="dialog" aria-modal="true" onClick={(e) => e.target === e.currentTarget && !clearBusy && setClearing(null)}>
            <div className="modal">
              <div className="big-ic bad"><Icon name="refresh" size={24} /></div>
              <h3>Clear {a.name}'s test?</h3>
              <p className="muted">
                Their answers, marks and activity log for this attempt will be removed, and they can take the test again
                with new questions. Their details stay. A record of this attempt (status {a.status.toLowerCase().replace('_', ' ')}
                {a.totalMarks != null ? `, overall ${Math.round(a.totalMarks)}/100` : ''}) is kept in the log.
              </p>
              <div className="field">
                <label htmlFor="clear-reason">Reason <span className="faint">(optional)</span></label>
                <input id="clear-reason" className="input" maxLength={500} value={clearing.reason}
                  onChange={(e) => setClearing({ reason: e.target.value })} placeholder="e.g. Microphone failed during the meeting" />
              </div>
              <div className="actions">
                <button className="btn btn-ghost" onClick={() => setClearing(null)} disabled={clearBusy}>Cancel</button>
                <button className="btn btn-danger" onClick={clearTest} disabled={clearBusy}>{clearBusy ? 'Clearing…' : 'Clear test'}</button>
              </div>
            </div>
          </div>
        )}

        {printing
          ? <>{d.answers.map((x) => <AnswerPanel key={x.questionId} x={x} who={who} />)}{activity}</>
          : tab === 'ACTIVITY' ? activity
            : d.answers.filter((x) => x.part === tab).map((x) => <AnswerPanel key={x.questionId} x={x} who={who} />)}
      </div>
    </>
  );
}
