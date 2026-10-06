import { useAuth } from '../contexts/Auth';

const PARTS = [
  ['1', 'Teams message', '20 min'],
  ['2', 'Email', '20 min'],
  ['3', 'Meeting', '20 min'],
];

function MicrosoftMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 21 21" aria-hidden="true">
      <rect x="1" y="1" width="9" height="9" fill="#f25022" />
      <rect x="11" y="1" width="9" height="9" fill="#7fba00" />
      <rect x="1" y="11" width="9" height="9" fill="#00a4ef" />
      <rect x="11" y="11" width="9" height="9" fill="#ffb900" />
    </svg>
  );
}

export default function Login() {
  const { login, busy } = useAuth();

  return (
    <div className="login">
      <section className="login-hero">
        <div>
          <div className="eyebrow">Neutara &middot; Assessment</div>
          <h1>Communication Assessment</h1>
          <p>One continuous hour in three parts. Write the way you would for a real customer and a real team.</p>
          <div className="hero-parts">
            {PARTS.map(([n, label, t]) => (
              <div className="hero-part" key={n}><b>{n}</b><span>{label}</span><em>{t}</em></div>
            ))}
          </div>
        </div>
        <div className="hero-foot">For Neutara employees only. One attempt per person.</div>
      </section>

      <section className="login-side">
        <div className="login-card">
          <img className="logo" src="/neutara-logo.png" alt="Neutara Technologies" style={{ height: 96 }} />
          <div>
            <h2>Sign in to begin</h2>
            <p className="muted" style={{ marginTop: 6 }}>Use your Neutara Microsoft work account.</p>
          </div>
          <button className="btn btn-ms btn-block" onClick={login} disabled={busy}>
            <MicrosoftMark /> {busy ? 'Signing in…' : 'Sign in with Microsoft'}
          </button>
        </div>
      </section>
    </div>
  );
}
