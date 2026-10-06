import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import Icon from '../components/Icon';
import MeetingRoom from '../components/MeetingRoom';
import { initials } from '../components/CandidateShell';
import { useAuth } from '../contexts/Auth';
import { useToast } from '../contexts/Toast';
import useProctor from '../hooks/useProctor';
import api, { errorText } from '../services/api';

const AUTOSAVE_MS = 4000;
const words = (s) => (s?.trim() ? s.trim().split(/\s+/).length : 0);
const mmss = (ms) => {
  const s = Math.max(0, Math.ceil(ms / 1000));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
};
const clockTime = (d) => d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

export default function Exam() {
  const { me, refresh } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const [state, setState] = useState(null);
  const [drafts, setDrafts] = useState({});
  const [saveState, setSaveState] = useState({ tone: '', text: 'All changes saved' });
  const [now, setNow] = useState(Date.now());
  const [fullscreen, setFullscreen] = useState(Boolean(document.fullscreenElement));
  const [warning, setWarning] = useState(null);
  const [confirm, setConfirm] = useState(false);
  const [elsewhere, setElsewhere] = useState(false);
  const [moving, setMoving] = useState(false);

  const offset = useRef(0);
  const dirty = useRef(new Set());
  const draftsRef = useRef(drafts);
  draftsRef.current = drafts;
  const stateRef = useRef(state);
  stateRef.current = state;
  const timedOut = useRef(false);

  const key = () => sessionStorage.getItem('cfa_session');

  // ------------------------------------------------------------------ loading the state
  const apply = useCallback((s) => {
    if (s.status === 'DONE') {
      sessionStorage.removeItem('cfa_session');
      refresh();
      navigate('/done', { replace: true, state: { terminated: s.violations >= s.maxViolations } });
      return;
    }
    if (s.sessionKey) sessionStorage.setItem('cfa_session', s.sessionKey);
    offset.current = s.serverNow - Date.now();
    timedOut.current = false;
    setState(s);
    setDrafts(Object.fromEntries(s.questions.map((q) => [q.id, {
      subject: q.subject || '', content: q.content || '', keys: 0, paste: 0,
    }])));
    dirty.current.clear();
  }, [navigate, refresh]);

  const fail = useCallback((e) => {
    if (e?.response?.status === 404 && /not started/i.test(errorText(e))) {
      sessionStorage.removeItem('cfa_session');
      refresh().then(() => navigate('/instructions', { replace: true }));
      return true;
    }
    if (e?.response?.status === 409 && /another window/i.test(errorText(e))) {
      setElsewhere(true);
      return true;
    }
    if (e?.response?.status === 409 && /already ended/i.test(errorText(e))) {
      navigate('/done', { replace: true });
      return true;
    }
    return false;
  }, [navigate, refresh]);

  useEffect(() => {
    const initial = location.state?.initial;
    if (initial) {
      apply(initial);
      return;
    }
    const k = key();
    const load = k
      ? api.get('/test/state', { headers: { 'X-Session-Key': k } })
      : api.post('/test/resume').then((r) => {
        toast('The test was reopened in a new window. This has been recorded.', 'bad');
        return r;
      });
    load.then(({ data }) => apply(data)).catch((e) => {
      if (!fail(e)) toast(errorText(e), 'bad');
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ------------------------------------------------------------------ saving
  const flush = useCallback(async () => {
    const ids = [...dirty.current];
    if (!ids.length || !stateRef.current) return true;
    setSaveState({ tone: '', text: 'Saving…' });
    try {
      for (const id of ids) {
        const d = draftsRef.current[id];
        await api.post('/test/answer', {
          sessionKey: key(), questionId: Number(id), subject: d.subject, content: d.content,
          keystrokes: d.keys, pasteBlocked: d.paste,
        });
        dirty.current.delete(id);
      }
      setSaveState({ tone: 'ok', text: `Saved ${clockTime(new Date())}` });
      return true;
    } catch (e) {
      if (!fail(e)) setSaveState({ tone: 'err', text: 'Not saved, retrying…' });
      return false;
    }
  }, [fail]);

  useEffect(() => {
    const t = setInterval(() => { flush(); }, AUTOSAVE_MS);
    return () => clearInterval(t);
  }, [flush]);

  const edit = (id, field) => (e) => {
    const value = e.target.value;
    dirty.current.add(String(id));
    setDrafts((d) => ({ ...d, [id]: { ...d[id], [field]: value, keys: d[id].keys + 1 } }));
  };
  const blockedPaste = (id) => (e) => {
    e.preventDefault();
    setDrafts((d) => ({ ...d, [id]: { ...d[id], paste: d[id].paste + 1 } }));
    dirty.current.add(String(id));
  };

  // ------------------------------------------------------------------ moving on
  const advance = useCallback(async (reason) => {
    if (moving) return;
    setMoving(true);
    setConfirm(false);
    await flush();
    try {
      const last = stateRef.current.partNumber === stateRef.current.partCount;
      const url = last && reason !== 'time' ? '/test/submit' : '/test/next';
      const { data } = await api.post(url, { sessionKey: key() });
      if (reason === 'time' && data.status !== 'DONE') toast(`Time is up. Part ${data.partNumber} has started.`);
      apply(data);
      window.scrollTo({ top: 0 });
    } catch (e) {
      if (!fail(e)) toast(errorText(e), 'bad');
    } finally {
      setMoving(false);
    }
  }, [apply, fail, flush, moving, toast]);

  // The clock. The deadline is the server's; the browser only counts down to it.
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(t);
  }, []);
  const remaining = state ? state.deadline - (now + offset.current) : 0;
  useEffect(() => {
    if (state && remaining <= 0 && !timedOut.current) {
      timedOut.current = true;
      advance('time');
    }
  }, [remaining, state, advance]);

  // ------------------------------------------------------------------ proctoring
  const report = useCallback(async (type, detail) => {
    if (!stateRef.current) return;
    try {
      const { data } = await api.post('/test/event', { sessionKey: key(), type, detail });
      if (data.terminated) {
        sessionStorage.removeItem('cfa_session');
        refresh();
        navigate('/done', { replace: true, state: { terminated: true } });
        return;
      }
      if (data.counted) {
        setState((s) => (s ? { ...s, violations: data.violations } : s));
        setWarning({ count: data.violations, max: data.maxViolations, type });
      }
    } catch (e) {
      fail(e);
    }
  }, [fail, navigate, refresh]);

  useProctor({ active: Boolean(state) && !elsewhere, report, onFullscreen: setFullscreen });

  const reenter = async () => {
    try { await document.documentElement.requestFullscreen(); } catch { toast('Please allow full screen to continue.', 'bad'); }
  };
  const takeOver = async () => {
    try {
      const { data } = await api.post('/test/resume');
      setElsewhere(false);
      apply(data);
    } catch (e) {
      toast(errorText(e), 'bad');
    }
  };

  if (!state) return <div className="center-load"><div className="spinner" /></div>;

  const partMs = (state.parts[state.partNumber - 1]?.minutes || 20) * 60 * 1000;
  const pct = Math.max(0, Math.min(100, (remaining / partMs) * 100));
  const last = state.partNumber === state.partCount;
  const clockClass = remaining <= 60_000 ? 'out' : remaining <= 5 * 60_000 ? 'low' : '';

  return (
    <div className="exam">
      <header className="exam-bar">
        <div className="exam-bar-inner">
          <div className="logo-chip"><img src="/neutara-mark.png" alt="Neutara" /><span className="wordmark">neutara</span></div>
          <nav className="parts" aria-label="Parts">
            {state.parts.map((p, i) => (
              <div key={p.part} className={`part-pill ${p.state}`}>
                <i>{p.state === 'DONE' ? <Icon name="check" size={12} stroke={3} /> : i + 1}</i>
                <span>{p.label}</span>
              </div>
            ))}
          </nav>
          <div className="strikes" title="Violations recorded">
            {Array.from({ length: state.maxViolations }).map((_, i) => <b key={i} className={i < state.violations ? 'hit' : ''} />)}
          </div>
          <div className={`clock ${clockClass}`} aria-live="off">
            <small>Part {state.partNumber} ends in</small>
            <strong>{mmss(remaining)}</strong>
          </div>
        </div>
        <div className="timebar"><div style={{ width: `${pct}%` }} /></div>
      </header>

      <main className="exam-body">
        {state.part === 'MEETING' && state.questions[0] && (
          <MeetingRoom key={state.questions[0].id} question={state.questions[0]}
            onEnded={() => toast('The meeting has ended. Submit your test when you are ready.', 'ok')} />
        )}
        {state.part !== 'MEETING' && state.questions.map((q, i) => {
          const d = drafts[q.id] || { subject: '', content: '' };
          const email = state.part === 'EMAIL';
          return (
            <section className="q-block" key={q.id}>
              <div className="card q-brief">
                <div className="qno">
                  <span className="eyebrow">Part {state.partNumber} &middot; {state.partLabel}{state.questions.length > 1 ? ` · Question ${i + 1}` : ''}</span>
                </div>
                <h2>{q.title}</h2>
                {q.scenario && (
                  <div className="scenario">
                    <div className="from"><div className="avatar">{email ? <Icon name="mail" size={13} /> : <Icon name="chat" size={13} />}</div>Scenario</div>
                    {q.scenario}
                  </div>
                )}
                <div>
                  <div className="task-label">Your task</div>
                  <div className="task">{q.task}</div>
                </div>
              </div>

              <div className={`card composer ${email ? 'mail' : 'teams'}`}>
                <div className="composer-head">
                  <span className={`app-ic ${email ? 'mail' : 'teams'}`}><Icon name={email ? 'mail' : 'chat'} size={15} /></span>
                  {email ? 'New email' : state.part === 'TEAMS' ? 'Microsoft Teams reply' : 'Your response'}
                  <span className={`saved ${saveState.tone}`}>{saveState.tone === 'ok' && <Icon name="check" size={13} />}{saveState.text}</span>
                </div>
                {email && (
                  <>
                    <div className="mail-line"><span>From</span><div className="to">{me?.name} &lt;{me?.email}&gt;</div></div>
                    <div className="mail-line">
                      <span>Subject</span>
                      <input value={d.subject} onChange={edit(q.id, 'subject')} onPaste={blockedPaste(q.id)}
                        maxLength={500} placeholder="Write a clear subject line" spellCheck autoComplete="off" />
                    </div>
                  </>
                )}
                <textarea className="compose-area" value={d.content} onChange={edit(q.id, 'content')}
                  onPaste={blockedPaste(q.id)} onDrop={(e) => e.preventDefault()} maxLength={20000}
                  placeholder={email ? 'Write your email here…' : 'Type your message…'} spellCheck aria-label="Your answer" />
                <div className="composer-foot">
                  <span className="num">{words(d.content)} words</span>
                  <span>Pasting is disabled</span>
                </div>
              </div>
            </section>
          );
        })}
      </main>

      <footer className="exam-foot">
        <div className="exam-foot-inner">
          <div className="who">
            <div className="avatar">{initials(me?.name)}</div>
            <span className="muted">{me?.name} &middot; {me?.employeeId}</span>
          </div>
          <button className="btn btn-primary" onClick={() => setConfirm(true)} disabled={moving}>
            {last ? 'Submit test' : `Finish part ${state.partNumber} & continue`} <Icon name="arrow" size={16} />
          </button>
        </div>
      </footer>

      {confirm && (
        <div className="overlay" role="dialog" aria-modal="true">
          <div className="modal">
            <div className="big-ic blue"><Icon name={last ? 'flag' : 'arrow'} size={24} /></div>
            <h3>{last ? 'Submit your test?' : `Finish part ${state.partNumber}?`}</h3>
            <p className="muted">
              {last
                ? (state.part === 'MEETING'
                  ? 'The meeting will end and your test will be submitted. This cannot be undone.'
                  : 'Your answers will be submitted and the test will end. This cannot be undone.')
                : `You will move to part ${state.partNumber + 1} and cannot come back to this part. Its remaining ${mmss(remaining)} will not carry over.`}
            </p>
            <div className="actions">
              <button className="btn btn-ghost" onClick={() => setConfirm(false)}>Keep writing</button>
              <button className="btn btn-primary" onClick={() => advance('user')} disabled={moving}>{last ? 'Submit' : 'Continue'}</button>
            </div>
          </div>
        </div>
      )}

      {warning && fullscreen && (
        <div className="overlay" role="alertdialog" aria-modal="true">
          <div className="modal">
            <div className="big-ic bad"><Icon name="alert" size={24} /></div>
            <h3>Warning {warning.count} of {warning.max}</h3>
            <p className="muted">
              Leaving the test window has been recorded and will appear in your report.
              {warning.max - warning.count === 1 ? ' One more and your test will end.' : ` ${warning.max - warning.count} more and your test will end.`}
            </p>
            <div className="actions"><button className="btn btn-primary" onClick={() => setWarning(null)}>Back to my test</button></div>
          </div>
        </div>
      )}

      {!fullscreen && !elsewhere && (
        <div className="overlay" role="alertdialog" aria-modal="true">
          <div className="modal">
            <div className="big-ic bad"><Icon name="screen" size={24} /></div>
            <h3>Return to full screen</h3>
            <p className="muted">The test must stay in full screen. Leaving it is recorded{state.violations ? ` (${state.violations} of ${state.maxViolations} so far)` : ''}. The clock is still running.</p>
            <div className="actions"><button className="btn btn-primary" onClick={reenter}>Enter full screen</button></div>
          </div>
        </div>
      )}

      {elsewhere && (
        <div className="overlay" role="alertdialog" aria-modal="true">
          <div className="modal">
            <div className="big-ic bad"><Icon name="lock" size={24} /></div>
            <h3>Your test is open in another window</h3>
            <p className="muted">Only one window can hold the test. Continue in the other window, or take it over here. Taking it over is recorded as a violation.</p>
            <div className="actions"><button className="btn btn-danger" onClick={takeOver}>Continue here</button></div>
          </div>
        </div>
      )}
    </div>
  );
}
