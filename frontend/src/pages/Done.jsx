import { useEffect, useState } from 'react';
import Icon from '../components/Icon';
import { useAuth } from '../contexts/Auth';
import api from '../services/api';

/** The end: submitted (or ended), then the candidate's marks once marking has finished. Marks only. */
export default function Done() {
  const { me, logout } = useAuth();
  const [res, setRes] = useState(null);

  useEffect(() => {
    sessionStorage.removeItem('cfa_session');
    if (document.fullscreenElement) document.exitFullscreen?.().catch(() => {});
  }, []);

  // Marking takes a few seconds per answer; ask again until it is done.
  useEffect(() => {
    let alive = true;
    let timer;
    const load = () => api.get('/test/result').then(({ data }) => {
      if (!alive) return;
      setRes(data);
      if (!data.ready) timer = setTimeout(load, 5000);
    }).catch(() => { if (alive) timer = setTimeout(load, 8000); });
    load();
    return () => { alive = false; clearTimeout(timer); };
  }, []);

  const terminated = res?.status === 'TERMINATED';
  return (
    <div className="done-wrap">
      <div className="card done-card">
        <img className="logo" src="/neutara-logo.png" alt="Neutara Technologies" style={{ height: 84 }} />
        {!res?.ready ? (
          <div className={`seal ${terminated ? 'bad' : ''}`}>
            <Icon name={terminated ? 'alert' : 'check'} size={38} stroke={2.2} />
          </div>
        ) : (
          <div className="ring big" style={{ '--p': res.total ?? 0 }}>
            <div><span><strong>{res.total}</strong><br /><small>out of 100</small></span></div>
          </div>
        )}
        <h1>{terminated ? 'Your test has ended' : 'Your test has been submitted'}</h1>
        <p className="muted" style={{ maxWidth: 420 }}>
          {terminated
            ? 'The test was closed because the rules were broken three times. Your answers up to that point have been marked.'
            : `Thank you, ${me?.name?.split(' ')[0] || ''}. Your answers have been recorded.`}
        </p>

        {res && (
          <div className="result-parts">
            {res.parts.map((p) => (
              <div key={p.part} className="result-part">
                <div className="result-row">
                  <span>{p.label}</span>
                  <span className="num">{res.ready ? <><b>{p.marks}</b> / {p.maxMarks}</> : <span className="faint">/ {p.maxMarks}</span>}</span>
                </div>
                <div className="meter"><div style={{ width: res.ready ? `${(p.marks / p.maxMarks) * 100}%` : '0%' }} /></div>
              </div>
            ))}
            {!res.ready && (
              <div className="marking"><div className="spinner" style={{ width: 18, height: 18, borderWidth: 2 }} /> Marking your answers… this takes about a minute.</div>
            )}
          </div>
        )}

        <button className="btn btn-ghost" onClick={logout}><Icon name="logout" size={16} /> Sign out</button>
      </div>
    </div>
  );
}
