import Icon from '../../components/Icon';

export const STATUS = {
  SUBMITTED: { label: 'Submitted', cls: 'chip-ok' },
  IN_PROGRESS: { label: 'In progress', cls: 'chip-blue' },
  TERMINATED: { label: 'Terminated', cls: 'chip-bad' },
  NOT_STARTED: { label: 'Not started', cls: 'chip-grey' },
  DETAILS_PENDING: { label: 'Details pending', cls: 'chip-warn' },
};

export function StatusChip({ status }) {
  const s = STATUS[status] || { label: status, cls: 'chip-grey' };
  return <span className={`chip ${s.cls}`}><span className="dot" />{s.label}</span>;
}

export function Violations({ count, max = 3 }) {
  return (
    <span className="vio" title={`${count} violation${count === 1 ? '' : 's'}`}>
      {Array.from({ length: max }).map((_, i) => <b key={i} className={i < count ? 'hit' : ''} />)}
    </span>
  );
}

export function Marks({ value, max }) {
  if (value == null) return <span className="faint">&mdash;</span>;
  const p = max ? Math.round((value / max) * 100) : 0;
  return (
    <span className="mark-cell" title={`${p}%`}>
      <span className="mark">{Math.round(value)}<small> / {Math.round(max)}</small></span>
      <span className="mini-bar"><span style={{ width: `${p}%` }} /></span>
    </span>
  );
}

/** Overall out of 100, coloured so strong and weak results stand out in a long list. */
export const band = (v) => (v == null ? '' : v >= 70 ? 'hi' : v >= 45 ? 'mid' : 'lo');

export function Total({ row }) {
  if (!row.attemptId) return <span className="faint">&mdash;</span>;
  if (row.status === 'IN_PROGRESS') return <span className="faint">In progress</span>;
  if (row.totalMarks == null) return <span className="chip chip-warn">Marking…</span>;
  return <span className={`overall-pill ${band(row.totalMarks)}`}>{Math.round(row.totalMarks)}</span>;
}

export const minutes = (s) => (s == null ? '—' : `${Math.round(s / 60)} min`);

export function TeamSelect({ teams, value, onChange }) {
  return (
    <select className="select" value={value} onChange={(e) => onChange(e.target.value)} aria-label="Team">
      <option value="">All teams</option>
      {teams.map((t) => <option key={t} value={t}>{t}</option>)}
    </select>
  );
}

/** The formatted Excel report: every candidate, marks per exam and overall, plus a team summary. */
export function downloadCsv(api, team) {
  return api.get('/admin/report.xlsx', { params: team ? { team } : {}, responseType: 'blob' }).then((r) => {
    const url = URL.createObjectURL(r.data);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Neutara CommuniQ Report${team ? ` - ${team}` : ''}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  });
}

export function ExportButton({ onClick }) {
  return <button className="btn btn-primary btn-sm" onClick={onClick}><Icon name="download" size={15} /> Download Excel report</button>;
}
