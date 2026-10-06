import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import CandidateShell from '../components/CandidateShell';
import Icon from '../components/Icon';
import { useAuth } from '../contexts/Auth';
import { useToast } from '../contexts/Toast';
import api, { errorText } from '../services/api';

const PART_ICON = { TEAMS: 'chat', EMAIL: 'mail', MEETING: 'meeting' };
const PART_TEXT = {
  TEAMS: 'Reply in a Microsoft Teams chat: short, clear and professional.',
  EMAIL: 'Write a complete customer email, with a subject line.',
  MEETING: 'A live meeting: an AI participant talks with you and asks questions. You reply by speaking.',
};

export default function Instructions() {
  const { me, refresh } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [ov, setOv] = useState(null);
  const [agree, setAgree] = useState(false);
  const [starting, setStarting] = useState(false);
  const [mic, setMic] = useState('');   // '', 'checking', 'ok', 'blocked'
  const hasMeeting = ov?.parts?.some((p) => p.part === 'MEETING');

  // Asked here, before the test, so the browser's permission pop-up never appears mid-meeting.
  const checkMic = async () => {
    setMic('checking');
    window.__cfaPrompt = true;
    try {
      const s = await navigator.mediaDevices.getUserMedia({ audio: true });
      s.getTracks().forEach((t) => t.stop());
      setMic('ok');
    } catch {
      setMic('blocked');
    } finally {
      window.__cfaPrompt = false;
    }
  };

  useEffect(() => {
    api.get('/test/overview').then(({ data }) => setOv(data)).catch((e) => toast(errorText(e), 'bad'));
  }, [toast]);

  const start = async () => {
    setStarting(true);
    // Full screen must be requested inside the click itself, or the browser refuses it.
    try { await document.documentElement.requestFullscreen?.(); } catch { /* checked again on the exam screen */ }
    try {
      const { data } = await api.post('/test/start');
      sessionStorage.setItem('cfa_session', data.sessionKey);
      await refresh();
      navigate('/test', { replace: true, state: { initial: data } });
    } catch (e) {
      toast(errorText(e), 'bad');
      if (document.fullscreenElement) document.exitFullscreen?.();
      setStarting(false);
    }
  };

  const ready = ov && ov.parts.length > 0;
  return (
    <CandidateShell step={2}>
      <div className="intro-head">
        <div>
          <div className="eyebrow">Before you begin</div>
          <h1>Hello {me?.name?.split(' ')[0]}, here is how it works</h1>
        </div>
        {ready && (
          <div className="total-time">
            <strong className="num">{ov.totalMinutes} min</strong>
            <span className="muted">one continuous sitting</span>
          </div>
        )}
      </div>

      {ov && !ready && (
        <div className="card rules"><p>The test is not open yet. Please check back later.</p></div>
      )}

      {ready && (
        <>
          <div className="timeline" style={{ '--n': ov.parts.length }}>
            {ov.parts.map((p, i) => (
              <div className="card tl-part" key={p.part}>
                <div className="ic"><Icon name={PART_ICON[p.part]} /></div>
                <div className="n">Part {i + 1}</div>
                <h3>{p.label}</h3>
                <p className="muted" style={{ fontSize: 13, paddingRight: 40 }}>{PART_TEXT[p.part]}</p>
                <div className="meta">
                  <span className="chip chip-blue"><Icon name="clock" size={13} /> {p.minutes} min</span>
                  <span className="chip chip-grey">{p.questions} {p.questions === 1 ? 'question' : 'questions'}</span>
                </div>
              </div>
            ))}
          </div>

          <div className="card rules">
            <h3>Rules for this test</h3>
            <ul>
              <li><Icon name="clock" />The parts run back to back. Each part closes on its own when its time ends, and you cannot go back to a part.</li>
              <li><Icon name="check" />Your writing saves automatically every few seconds.</li>
              <li><Icon name="screen" />The test runs in full screen. Stay in it until you finish.</li>
              <li><Icon name="copy" />Copy, paste and right-click are switched off. Type your own answers.</li>
              {hasMeeting && <li><Icon name="meeting" />In part 3 the AI plays the other person in a meeting. You reply by speaking; it may ask follow-up questions.</li>}
              <li className="alert"><Icon name="alert" />Switching tabs, leaving full screen or leaving the window is recorded. <b>On the {ov.maxViolations === 3 ? 'third' : `${ov.maxViolations}th`} time, the test ends.</b></li>
              <li className="alert"><Icon name="flag" />You have <b>one attempt</b>. Once you start, the clock keeps running even if you close the window.</li>
            </ul>
            {hasMeeting && (
              <div className={`agree mic-check ${mic}`}>
                <Icon name="meeting" />
                <span style={{ flex: 1 }}>
                  {mic === 'ok' ? 'Microphone ready.' : mic === 'blocked'
                    ? 'Microphone blocked. Click the camera/microphone icon in the address bar, choose Allow, then check again.'
                    : 'Part 3 is a spoken meeting. Allow your microphone before you start (required).'}
                </span>
                {mic !== 'ok' && <button type="button" className="btn btn-ghost btn-sm" onClick={checkMic} disabled={mic === 'checking'}>{mic === 'checking' ? 'Checking…' : 'Check microphone'}</button>}
              </div>
            )}
            <label className="agree">
              <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
              I have read the rules and I am ready to start.
            </label>
            <div className="start-row">
              <Link to="/profile" className="muted" style={{ fontSize: 13 }}>Edit my details</Link>
              <button className="btn btn-primary" disabled={!agree || starting || (hasMeeting && mic !== 'ok')} onClick={start}
                title={hasMeeting && mic !== 'ok' ? 'Allow your microphone first' : undefined}>
                {starting ? 'Starting…' : 'Start the test'} <Icon name="arrow" size={16} />
              </button>
            </div>
          </div>
        </>
      )}
    </CandidateShell>
  );
}
