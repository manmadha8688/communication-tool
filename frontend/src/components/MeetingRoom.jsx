import { useCallback, useEffect, useRef, useState } from 'react';
import Icon from './Icon';
import { useToast } from '../contexts/Toast';
import api, { errorText } from '../services/api';

const key = () => sessionStorage.getItem('cfa_session');
const MAX_RECORD_SECONDS = 120;

/**
 * Part 3: a live meeting with an AI participant.
 *
 * The AI side runs over a live connection, so it answers in about a second and its words appear as
 * it speaks them. The candidate's side is PRESS TO TALK: press the microphone, speak, press again to
 * stop, check what was heard, then Send (or record again). Nothing is answered until Send, so noise,
 * pauses and the AI's own voice through the speakers can never start a turn.
 */
export default function MeetingRoom({ question, onEnded }) {
  const toast = useToast();
  const [turns, setTurns] = useState([]);          // [{id, role: 'AI'|'YOU', text, pending}]
  const [status, setStatus] = useState('idle');    // idle | connecting | live | ending | ended | error
  const [aiTalking, setAiTalking] = useState(false);
  const [recording, setRecording] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [draft, setDraft] = useState(null);        // { itemId, text } | { itemId, hearing: true }
  const [thinking, setThinking] = useState(false);
  const [limits, setLimits] = useState({ min: 5, max: 12 });
  const pc = useRef(null);
  const dc = useRef(null);
  const mic = useRef(null);
  const audioEl = useRef(null);
  const turnsRef = useRef([]);
  const draftRef = useRef(null);
  const ending = useRef(null);
  const finished = useRef(false);
  const timeUpSent = useRef(false);
  const saveTimer = useRef(null);
  const recordTimer = useRef(null);
  const endRef = useRef(null);

  turnsRef.current = turns;
  draftRef.current = draft;
  const replies = turns.filter((t) => t.role === 'YOU' && t.text).length;

  // A reload in the middle of the meeting shows what was already said.
  useEffect(() => {
    api.get('/test/meeting', { headers: { 'X-Session-Key': key() } }).then(({ data }) => {
      if (data.turns?.length) setTurns(data.turns.map((t, i) => ({ ...t, id: `saved-${i}`, pending: false })));
      if (data.ended) setStatus('ended');
    }).catch(() => {});
  }, []);

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }); }, [turns, draft, thinking]);

  useEffect(() => {
    if (!recording) return undefined;
    setSeconds(0);
    const t = setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => clearInterval(t);
  }, [recording]);

  const send = (obj) => { if (dc.current?.readyState === 'open') dc.current.send(JSON.stringify(obj)); };
  const micOn = (on) => mic.current?.getAudioTracks().forEach((t) => { t.enabled = on; });

  // ------------------------------------------------------------------ the record
  const persist = useCallback(() => {
    clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      const done = turnsRef.current.filter((t) => !t.pending && t.text?.trim()).map((t) => ({ role: t.role, text: t.text.trim(), at: t.at }));
      api.post('/test/meeting/transcript', { sessionKey: key(), turns: done }).catch(() => {});
    }, 300);
  }, []);

  const upsertAi = useCallback((id, fn) => {
    setTurns((xs) => {
      const i = xs.findIndex((x) => x.id === id);
      if (i === -1) return [...xs, fn({ id, role: 'AI', text: '', pending: true, at: new Date().toISOString() })];
      const copy = xs.slice();
      copy[i] = fn(copy[i]);
      return copy;
    });
  }, []);

  // ------------------------------------------------------------------ closing
  const hangUp = useCallback(() => {
    clearTimeout(recordTimer.current);
    try { mic.current?.getTracks().forEach((t) => t.stop()); } catch { /* stopped */ }
    try { dc.current?.close(); } catch { /* closed */ }
    try { pc.current?.close(); } catch { /* closed */ }
    pc.current = null;
    dc.current = null;
  }, []);

  const finish = useCallback(async (satisfied) => {
    if (finished.current) return;
    finished.current = true;
    setStatus('ending');
    await new Promise((r) => setTimeout(r, 1200));
    hangUp();
    const done = turnsRef.current.filter((t) => !t.pending && t.text?.trim()).map((t) => ({ role: t.role, text: t.text.trim(), at: t.at }));
    try {
      await api.post('/test/meeting/transcript', { sessionKey: key(), turns: done });
      await api.post('/test/meeting/end', { sessionKey: key(), satisfied: Boolean(satisfied) });
    } catch (e) {
      toast(errorText(e), 'bad');
    }
    setStatus('ended');
    onEnded?.();
  }, [hangUp, onEnded, toast]);

  useEffect(() => () => hangUp(), [hangUp]);

  // ------------------------------------------------------------------ events from the AI
  const onEvent = useCallback((e) => {
    let ev;
    try { ev = JSON.parse(e.data); } catch { return; }
    switch (ev.type) {
      case 'input_audio_buffer.committed':
        setDraft({ itemId: ev.item_id, hearing: true });
        break;
      case 'conversation.item.input_audio_transcription.completed': {
        if (draftRef.current?.itemId !== ev.item_id) break;
        const text = (ev.transcript || '').trim();
        const words = text.split(/\s+/).filter((w) => /[a-z0-9]/i.test(w)).length;
        if (words < 3) {
          send({ type: 'conversation.item.delete', item_id: ev.item_id });
          setDraft(null);
          toast('That was too short to hear clearly. Please record your reply again.', 'bad');
        } else {
          setDraft({ itemId: ev.item_id, text });
        }
        break;
      }
      case 'conversation.item.input_audio_transcription.failed':
        if (draftRef.current?.itemId === ev.item_id) {
          send({ type: 'conversation.item.delete', item_id: ev.item_id });
          setDraft(null);
          toast('Your reply could not be heard. Please record it again.', 'bad');
        }
        break;
      case 'response.created':
        setThinking(true);
        break;
      case 'response.output_audio_transcript.delta':
        setThinking(false);
        upsertAi(ev.item_id, (t) => ({ ...t, text: t.text + (ev.delta || '') }));
        break;
      case 'response.output_audio_transcript.done':
        upsertAi(ev.item_id, (t) => ({ ...t, text: (ev.transcript || t.text).trim(), pending: false }));
        persist();
        break;
      case 'response.done':
        setThinking(false);
        break;
      case 'output_audio_buffer.started':
        setAiTalking(true);
        break;
      case 'output_audio_buffer.stopped':
        setAiTalking(false);
        if (ending.current) finish(ending.current.satisfied);
        break;
      case 'response.function_call_arguments.done':
        if (ev.name === 'end_meeting') {
          let satisfied = false;
          try { satisfied = Boolean(JSON.parse(ev.arguments || '{}').satisfied); } catch { /* not satisfied */ }
          ending.current = { satisfied };
          setTimeout(() => finish(satisfied), 6000);
        }
        break;
      case 'error':
        if (ev.error?.message) console.warn('meeting', ev.error.message);
        break;
      default:
    }
  }, [finish, persist, toast, upsertAi]);

  // ------------------------------------------------------------------ connecting
  const connect = async () => {
    setStatus('connecting');
    try {
      const { data } = await api.post('/test/meeting/session', { sessionKey: key() });
      setLimits({ min: data.minReplies, max: data.maxReplies });
      if (data.turns?.length) setTurns(data.turns.map((t, i) => ({ ...t, id: `saved-${i}`, pending: false })));

      const peer = new RTCPeerConnection();
      pc.current = peer;
      peer.ontrack = (e) => { if (audioEl.current) audioEl.current.srcObject = e.streams[0]; };
      peer.onconnectionstatechange = () => {
        if (['failed', 'disconnected'].includes(peer.connectionState) && !ending.current) setStatus('error');
      };

      window.__cfaPrompt = true;
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
      window.__cfaPrompt = false;
      mic.current = stream;
      stream.getAudioTracks().forEach((t) => { t.enabled = false; });   // only while recording
      stream.getTracks().forEach((t) => peer.addTrack(t, stream));

      const channel = peer.createDataChannel('oai-events');
      dc.current = channel;
      channel.onmessage = onEvent;
      channel.onopen = () => {
        setStatus('live');
        channel.send(JSON.stringify({ type: 'response.create' }));     // the participant speaks first
      };

      const offer = await peer.createOffer();
      await peer.setLocalDescription(offer);
      const res = await fetch(`https://api.openai.com/v1/realtime/calls?model=${encodeURIComponent(data.model)}`, {
        method: 'POST',
        body: offer.sdp,
        headers: { Authorization: `Bearer ${data.secret}`, 'Content-Type': 'application/sdp' },
      });
      if (!res.ok) throw new Error('The meeting could not connect. Please try again.');
      await peer.setRemoteDescription({ type: 'answer', sdp: await res.text() });
    } catch (e) {
      window.__cfaPrompt = false;
      hangUp();
      setStatus('error');
      toast(e?.response ? errorText(e) : (e?.message || 'The meeting could not connect.'), 'bad');
    }
  };

  // ------------------------------------------------------------------ your turn
  const startRecording = () => {
    if (draft?.itemId) send({ type: 'conversation.item.delete', item_id: draft.itemId });   // replace it
    setDraft(null);
    send({ type: 'input_audio_buffer.clear' });
    micOn(true);
    setRecording(true);
    recordTimer.current = setTimeout(stopRecording, MAX_RECORD_SECONDS * 1000);   // eslint-disable-line no-use-before-define
  };

  const stopRecording = () => {
    clearTimeout(recordTimer.current);
    // A short tail so the last word is not clipped.
    setTimeout(() => {
      micOn(false);
      send({ type: 'input_audio_buffer.commit' });
    }, 350);
    setRecording(false);
  };

  const sendReply = () => {
    if (!draft?.text) return;
    const turn = { id: draft.itemId, role: 'YOU', text: draft.text, pending: false, at: new Date().toISOString() };
    setTurns((xs) => [...xs, turn]);
    turnsRef.current = [...turnsRef.current, turn];
    setDraft(null);
    persist();
    setThinking(true);
    const now = replies + 1;
    if (now >= limits.max && !timeUpSent.current) {
      timeUpSent.current = true;
      send({ type: 'response.create', response: { instructions: 'The meeting time is up. Reply briefly to what was just said, then give a short closing remark that is not a question, then call end_meeting.' } });
    } else {
      send({ type: 'response.create' });
    }
  };

  // ------------------------------------------------------------------ screen
  const live = status === 'live';
  const busyAi = aiTalking || thinking;
  const label = status === 'ended' ? 'Meeting ended'
    : status === 'ending' ? 'Closing the meeting…'
      : status === 'connecting' ? 'Connecting…'
        : !live ? 'Waiting to start'
          : aiTalking ? 'Speaking…' : thinking ? 'Thinking…' : recording ? 'Listening to you…' : 'Your turn';

  return (
    <section className="meeting">
      <div className="card q-brief meeting-brief">
        <span className="eyebrow">Part 3 &middot; Meeting</span>
        <h2>{question.title}</h2>
        <div className="scenario"><div className="from"><div className="avatar"><Icon name="meeting" size={13} /></div>Situation</div>{question.scenario}</div>
        <div><div className="task-label">Your task</div><div className="task">{question.task}</div></div>
        <div className="meeting-how">
          <b>How this works</b>
          <span>The other person in the meeting is played by AI. They speak first, and their words appear as they speak.</span>
          <span>On your turn: press the microphone, speak, press it again to stop. Check what was heard, then press Send.</span>
          <span>The meeting ends when they are satisfied.</span>
        </div>
      </div>

      <div className="card call">
        <div className="call-head">
          <div className={`call-avatar ${aiTalking ? 'talking' : ''}`}><Icon name="users" size={20} /></div>
          <div><b>Meeting participant</b><small>{label}</small></div>
          <span className="call-live">{status === 'ended' ? 'Ended' : live ? <><span className="dot" /> Live</> : status === 'connecting' ? 'Connecting' : 'Not started'}</span>
        </div>

        <div className="transcript" aria-live="polite">
          {(status === 'idle' || status === 'error') && turns.length === 0 && (
            <div className="call-start">
              <div className="big-ic blue"><Icon name="meeting" size={26} /></div>
              <p className="muted">Read the situation, then start the meeting. The participant will speak first.</p>
              <button className="btn btn-primary" onClick={connect}>{status === 'error' ? 'Try again' : 'Start meeting'} <Icon name="arrow" size={16} /></button>
            </div>
          )}
          {status === 'connecting' && turns.length === 0 && <div className="call-start"><div className="spinner" /><p className="muted">Connecting…</p></div>}
          {turns.filter((t) => t.text || t.pending).map((t) => (
            <div key={t.id} className={`bubble ${t.role === 'AI' ? 'them' : 'me'}`}>
              <small>{t.role === 'AI' ? 'Participant' : 'You'}</small>
              <p>{t.text || <span className="typing-dots"><span /><span /><span /></span>}</p>
            </div>
          ))}
          {thinking && !turns.some((t) => t.pending) && <div className="bubble them typing"><span /><span /><span /></div>}
          {(status === 'error' || (status === 'idle' && turns.length > 0)) && turns.length > 0 && (
            <div className="call-ended" style={{ background: 'var(--warn-soft)', color: 'var(--warn)' }}>
              The meeting is not connected. <button className="btn btn-ghost btn-sm" onClick={connect}>Reconnect</button>
            </div>
          )}
          {status === 'ended' && <div className="call-ended"><Icon name="check" size={18} /> The meeting has ended. Submit your test when you are ready.</div>}
          <div ref={endRef} />
        </div>

        {live && (
          <div className="call-reply">
            <div className={`spoken ${draft?.text ? '' : 'empty'}`} aria-live="polite">
              {recording ? 'Listening… press the microphone again when you finish.'
                : draft?.hearing ? 'Turning your voice into text…'
                  : draft?.text || (busyAi ? 'Wait for the participant to finish speaking.' : 'Press the microphone and speak your reply.')}
            </div>
            <div className="call-controls">
              <button className={`mic ${recording ? 'on' : ''}`} onClick={recording ? stopRecording : startRecording}
                disabled={busyAi || draft?.hearing} aria-label={recording ? 'Stop recording' : 'Record your reply'}>
                {recording ? <span className="stop-sq" /> : <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M5 11a7 7 0 0 0 14 0M12 18v3" /></svg>}
              </button>
              <span className="muted num" style={{ fontSize: 13 }}>
                {recording ? `Recording ${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
                  : draft?.text ? 'Press Send to reply' : busyAi ? 'Participant is speaking' : 'Press to speak'}
              </span>
              <button className="btn btn-primary" onClick={sendReply} disabled={!draft?.text || recording || busyAi}>
                Send reply <Icon name="arrow" size={16} />
              </button>
            </div>
          </div>
        )}
        <audio ref={audioEl} autoPlay />
      </div>
    </section>
  );
}
