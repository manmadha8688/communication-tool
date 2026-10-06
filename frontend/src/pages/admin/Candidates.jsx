import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useToast } from '../../contexts/Toast';
import api, { errorText } from '../../services/api';
import { ExportButton, Marks, StatusChip, TeamSelect, Total, Violations, downloadCsv, minutes } from './shared';

export default function Candidates() {
  const toast = useToast();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const team = params.get('team') || '';
  const [rows, setRows] = useState(null);
  const [teams, setTeams] = useState([]);
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');

  useEffect(() => {
    api.get('/admin/summary').then(({ data }) => setTeams(data.teams)).catch(() => {});
  }, []);
  useEffect(() => {
    api.get('/admin/attempts', { params: team ? { team } : {} })
      .then(({ data }) => setRows(data)).catch((e) => toast(errorText(e), 'bad'));
  }, [team, toast]);

  const shown = useMemo(() => (rows || []).filter((r) => {
    const t = q.trim().toLowerCase();
    const hit = !t || [r.name, r.email, r.employeeId, r.jobRole].some((v) => v?.toLowerCase().includes(t));
    return hit && (!status || r.status === status || (status === 'FLAGGED' && r.violations > 0));
  }), [rows, q, status]);

  const open = (id) => {
    // The report pages step through exactly the list the admin is looking at.
    try { sessionStorage.setItem('cfa_order', JSON.stringify(shown.filter((r) => r.attemptId).map((r) => r.attemptId))); } catch { /* private mode */ }
    navigate(`/admin/candidates/${id}`);
  };

  const partCols = useMemo(() => {
    const seen = new Map();
    (rows || []).forEach((r) => r.parts.forEach((p) => seen.set(p.part, p.label)));
    return [...seen.entries()];
  }, [rows]);

  return (
    <>
      <div className="main-head">
        <div><div className="eyebrow">Report</div><h1>Candidates</h1></div>
        <div className="toolbar">
          <input className="input search" placeholder="Search name, email, ID or role" value={q} onChange={(e) => setQ(e.target.value)} />
          <TeamSelect teams={teams} value={team} onChange={(t) => setParams(t ? { team: t } : {})} />
          <select className="select" value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
            <option value="">Every status</option>
            <option value="SUBMITTED">Submitted</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="TERMINATED">Terminated</option>
            <option value="NOT_STARTED">Not started</option>
            <option value="DETAILS_PENDING">Details pending</option>
            <option value="FLAGGED">Any violation</option>
          </select>
          <ExportButton onClick={() => downloadCsv(api, team).catch((e) => toast(errorText(e), 'bad'))} />
        </div>
      </div>
      <div className="main-body">
        <div className="card table-wrap">
          {!rows ? <div className="center-load"><div className="spinner" /></div> : (
            <table className="grid">
              <thead>
                <tr>
                  <th>Candidate</th><th>Team &middot; Role</th><th>Status</th><th>Time</th><th>Violations</th>
                  {partCols.map(([k, l]) => <th key={k}>{l}</th>)}
                  <th className="r">Overall / 100</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((r) => (
                  <tr key={r.attemptId ?? r.email} className={r.attemptId ? 'click' : ''}
                    onClick={() => r.attemptId && open(r.attemptId)}>
                    <td className="person"><b>{r.name || '—'}</b><small>{r.employeeId || 'No ID yet'} &middot; {r.email}</small></td>
                    <td className="person"><b style={{ fontWeight: 500 }}>{r.team || '—'}</b><small>{r.jobRole}</small></td>
                    <td><StatusChip status={r.status} /></td>
                    <td className="num">{minutes(r.durationSeconds)}</td>
                    <td><Violations count={r.violations} /></td>
                    {partCols.map(([k]) => {
                      const p = r.parts.find((x) => x.part === k);
                      return <td key={k}>{p ? <Marks value={p.marks} max={p.maxMarks} /> : <span className="faint">&mdash;</span>}</td>;
                    })}
                    <td className="r"><Total row={r} /></td>
                  </tr>
                ))}
                {!shown.length && <tr><td colSpan={6 + partCols.length} className="empty">No candidates match.</td></tr>}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}
