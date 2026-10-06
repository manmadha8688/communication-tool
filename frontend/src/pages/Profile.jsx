import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import CandidateShell from '../components/CandidateShell';
import Icon from '../components/Icon';
import { homeFor, useAuth } from '../contexts/Auth';
import { useToast } from '../contexts/Toast';
import api, { errorText } from '../services/api';

export default function Profile() {
  const { me, setMe } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [teams, setTeams] = useState([]);
  const [saving, setSaving] = useState(false);
  const [f, setF] = useState({
    name: me?.name || '', employeeId: me?.employeeId || '', team: me?.team || '', jobRole: me?.jobRole || '',
  });

  useEffect(() => {
    api.get('/teams').then(({ data }) => setTeams(data)).catch(() => {});
  }, []);

  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }));
  const locked = me?.testStatus !== 'NOT_STARTED';

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      const { data } = await api.put('/me/profile', f);
      setMe(data);
      navigate(homeFor(data));
    } catch (err) {
      toast(errorText(err), 'bad');
    } finally {
      setSaving(false);
    }
  };

  return (
    <CandidateShell step={1}>
      <div className="profile-grid">
        <div className="profile-aside">
          <div className="eyebrow">Step 2 of 4</div>
          <h1>Tell us who is taking the test</h1>
          <p>These details appear on your assessment record. Check them carefully: they cannot be changed once the test starts.</p>
        </div>
        <form className="card profile-form" onSubmit={submit}>
          <div className="field">
            <label htmlFor="name">Full name</label>
            <input id="name" className="input" value={f.name} onChange={set('name')} maxLength={120} required disabled={locked} autoComplete="name" />
          </div>
          <div className="row">
            <div className="field">
              <label htmlFor="emp">Employee ID</label>
              <input id="emp" className="input" value={f.employeeId} onChange={set('employeeId')} maxLength={64} required disabled={locked} placeholder="e.g. CF1024" />
            </div>
            <div className="field">
              <label htmlFor="role">Role</label>
              <input id="role" className="input" value={f.jobRole} onChange={set('jobRole')} maxLength={120} required disabled={locked} placeholder="e.g. Migration Engineer" />
            </div>
          </div>
          <div className="field">
            <label htmlFor="team">Team</label>
            <input id="team" className="input" list="team-list" value={f.team} onChange={set('team')} maxLength={120} required disabled={locked} placeholder="Choose your team or type a new one" />
            <datalist id="team-list">{teams.map((t) => <option key={t} value={t} />)}</datalist>
            <span className="hint">Pick from the list if your team is there, so everyone in it is reported together.</span>
          </div>
          <div className="locked-note"><Icon name="lock" size={16} /> Signed in as {me?.email}</div>
          <button className="btn btn-primary" disabled={saving || locked}>
            {saving ? 'Saving…' : 'Save and continue'} <Icon name="arrow" size={16} />
          </button>
        </form>
      </div>
    </CandidateShell>
  );
}
