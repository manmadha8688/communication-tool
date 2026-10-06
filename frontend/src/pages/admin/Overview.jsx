import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../../contexts/Toast';
import api, { errorText } from '../../services/api';
import { ExportButton, StatusChip, TeamSelect, Total, Violations, downloadCsv } from './shared';

export default function Overview() {
  const toast = useToast();
  const navigate = useNavigate();
  const [team, setTeam] = useState('');
  const [sum, setSum] = useState(null);
  const [rows, setRows] = useState([]);

  useEffect(() => {
    const params = team ? { team } : {};
    Promise.all([api.get('/admin/summary', { params }), api.get('/admin/attempts', { params })])
      .then(([s, r]) => { setSum(s.data); setRows(r.data); })
      .catch((e) => toast(errorText(e), 'bad'));
  }, [team, toast]);

  if (!sum) return <div className="center-load"><div className="spinner" /></div>;
  const maxTeam = Math.max(1, ...Object.values(sum.byTeam));
  const flagged = rows.filter((r) => r.violations > 0).slice(0, 6);
  const latest = [...rows.filter((r) => r.attemptId), ...rows.filter((r) => !r.attemptId)];

  return (
    <>
      <div className="main-head">
        <div><div className="eyebrow">Report</div><h1>Overview</h1></div>
        <div className="toolbar">
          <TeamSelect teams={sum.teams} value={team} onChange={setTeam} />
          <ExportButton onClick={() => downloadCsv(api, team).catch((e) => toast(errorText(e), 'bad'))} />
        </div>
      </div>
      <div className="main-body">
        <div className="kpis">
          <div className="card kpi accent"><span className="l">Average overall / 100</span><span className="v">{sum.averageMarks ?? '—'}</span></div>
          <div className="card kpi"><span className="l">Signed in</span><span className="v">{sum.candidates}</span></div>
          <div className="card kpi"><span className="l">Not started</span><span className="v" style={{ color: 'var(--ink-2)' }}>{sum.notStarted}</span></div>
          <div className="card kpi"><span className="l">Submitted</span><span className="v" style={{ color: 'var(--ok)' }}>{sum.completed}</span></div>
          <div className="card kpi"><span className="l">In progress</span><span className="v" style={{ color: 'var(--brand)' }}>{sum.inProgress}</span></div>
          <div className="card kpi"><span className="l">Terminated</span><span className="v" style={{ color: 'var(--bad)' }}>{sum.terminated}</span></div>
        </div>

        <div className="two-col">
          <div className="card">
            <div style={{ padding: '18px 22px 0' }}><h3 style={{ fontSize: 16 }}>Latest results</h3></div>
            <div className="table-wrap">
              <table className="grid">
                <thead><tr><th>Candidate</th><th>Team</th><th>Status</th><th>Violations</th><th className="r">Overall / 100</th></tr></thead>
                <tbody>
                  {latest.slice(0, 8).map((r) => (
                    <tr key={r.attemptId ?? r.email} className={r.attemptId ? 'click' : ''}
                      onClick={() => r.attemptId && navigate(`/admin/candidates/${r.attemptId}`)}>
                      <td className="person"><b>{r.name || '—'}</b><small>{r.employeeId || r.email}</small></td>
                      <td>{r.team || '—'}</td>
                      <td><StatusChip status={r.status} /></td>
                      <td><Violations count={r.violations} /></td>
                      <td className="r"><Total row={r} /></td>
                    </tr>
                  ))}
                  {!rows.length && <tr><td colSpan={5} className="empty">No one has taken the test yet.</td></tr>}
                </tbody>
              </table>
            </div>
          </div>

          <div style={{ display: 'grid', gap: 22 }}>
            <div className="card">
              <div style={{ padding: '18px 22px 0' }}><h3 style={{ fontSize: 16 }}>Candidates by team</h3></div>
              <div className="team-bars">
                {Object.entries(sum.byTeam).map(([t, n]) => (
                  <div className="team-bar" key={t}>
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t}</span>
                    <div className="track"><div style={{ width: `${(n / maxTeam) * 100}%` }} /></div>
                    <span className="num r">{n}</span>
                  </div>
                ))}
                {!Object.keys(sum.byTeam).length && <span className="faint">No teams yet.</span>}
              </div>
            </div>
            <div className="card">
              <div style={{ padding: '18px 22px 0' }}><h3 style={{ fontSize: 16 }}>Malpractice flagged</h3></div>
              <div className="team-bars">
                {flagged.map((r) => (
                  <div key={r.attemptId} className="team-bar" style={{ cursor: 'pointer', gridTemplateColumns: '1fr auto' }}
                    onClick={() => navigate(`/admin/candidates/${r.attemptId}`)}>
                    <span><b style={{ fontWeight: 600 }}>{r.name}</b> <span className="faint">&middot; {r.team}</span></span>
                    <Violations count={r.violations} />
                  </div>
                ))}
                {!flagged.length && <span className="faint">No violations recorded.</span>}
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
