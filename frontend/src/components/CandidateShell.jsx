import { useAuth } from '../contexts/Auth';
import Icon from './Icon';

const STEPS = ['Sign in', 'Your details', 'Instructions', 'Test'];

export const initials = (name = '') =>
  name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || '?';

/** The calm frame around the steps before the test. */
export default function CandidateShell({ step, children }) {
  const { me, logout } = useAuth();
  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-inner">
          <div className="brandmark">
            <img className="logo" src="/neutara-mark.png" alt="Neutara" style={{ height: 34 }} />
            <span className="wordmark">neutara</span>
            <div className="divider" />
            <span>CommuniQ</span>
          </div>
          <div className="spacer" />
          <div className="who">
            <div className="avatar">{initials(me?.name || me?.email)}</div>
            <span className="muted">{me?.email}</span>
            <button className="btn btn-ghost btn-sm" onClick={logout} title="Sign out"><Icon name="logout" size={16} /></button>
          </div>
        </div>
      </header>
      <main className="page">
        {step != null && (
          <div className="journey" aria-label="Progress">
            {STEPS.map((s, i) => (
              <div key={s} style={{ display: 'contents' }}>
                {i > 0 && <span className="bar" />}
                <span className={`step ${i < step ? 'done' : i === step ? 'on' : ''}`}>
                  <i>{i < step ? <Icon name="check" size={12} stroke={3} /> : i + 1}</i>{s}
                </span>
              </div>
            ))}
          </div>
        )}
        {children}
      </main>
    </div>
  );
}
