import { useCallback, useEffect, useState } from 'react';
import Icon from '../../components/Icon';
import { useToast } from '../../contexts/Toast';
import api, { errorText } from '../../services/api';

const PARTS = [['TEAMS', 'Teams message'], ['EMAIL', 'Email'], ['MEETING', 'Meeting']];
const blank = (part) => ({ id: null, code: '', category: '', part, ordinal: 1, title: '', scenario: '', task: '', metrics: [], active: true });

export default function Questions() {
  const toast = useToast();
  const [list, setList] = useState(null);
  const [part, setPart] = useState('TEAMS');
  const [edit, setEdit] = useState(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => api.get('/admin/questions').then(({ data }) => setList(data))
    .catch((e) => toast(errorText(e), 'bad')), [toast]);
  useEffect(() => { load(); }, [load]);

  const shown = (list || []).filter((q) => q.part === part);
  const set = (k) => (e) => setEdit((x) => ({ ...x, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));
  const setMetric = (i, k) => (e) => setEdit((x) => ({
    ...x, metrics: x.metrics.map((m, j) => (j === i ? { ...m, [k]: k === 'weight' ? Number(e.target.value) : e.target.value } : m)),
  }));

  const save = async () => {
    setSaving(true);
    try {
      const body = { ...edit, ordinal: Number(edit.ordinal) || 1 };
      if (edit.id) await api.put(`/admin/questions/${edit.id}`, body);
      else await api.post('/admin/questions', body);
      toast('Question saved.', 'ok');
      setEdit(null);
      load();
    } catch (e) {
      toast(errorText(e), 'bad');
    } finally {
      setSaving(false);
    }
  };
  const remove = async (q) => {
    try {
      await api.delete(`/admin/questions/${q.id}`);
      toast('Question removed (or switched off if it has been answered).', 'ok');
      load();
    } catch (e) {
      toast(errorText(e), 'bad');
    }
  };

  const weightSum = edit?.metrics.reduce((s, m) => s + (Number(m.weight) || 0), 0) ?? 0;

  return (
    <>
      <div className="main-head">
        <div><div className="eyebrow">Test content</div><h1>Questions</h1></div>
        <div className="toolbar">
          <div className="seg">
            {PARTS.map(([k, l]) => <button key={k} className={part === k ? 'on' : ''} onClick={() => setPart(k)}>{l}</button>)}
          </div>
          <button className="btn btn-primary btn-sm" onClick={() => setEdit(blank(part))}><Icon name="plus" size={15} /> Add question</button>
        </div>
      </div>
      <div className="main-body">
        <p className="muted" style={{ marginTop: -8 }}>
          Each candidate gets ONE active question per part, chosen at random from the ones given out least, so no two candidates share a question until every question has been used. The points to cover are the marking checklist and are never shown to candidates. A part with no active questions is left out of the test.
        </p>
        {!list ? <div className="center-load"><div className="spinner" /></div> : (
          <div className="q-list">
            {shown.map((q) => (
              <div className="card q-item" key={q.id}>
                <div>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 4 }}>
                    <span className="chip chip-blue">{q.code || `#${q.ordinal}`}</span>
                    <h4>{q.title}</h4>
                    {!q.active && <span className="chip chip-warn">Off</span>}
                    {q.timesUsed > 0 && <span className="chip chip-grey">Given to {q.timesUsed}</span>}
                  </div>
                  {q.category && <div className="hint" style={{ marginBottom: 4 }}>{q.category}</div>}
                  <p>{q.task}</p>
                  <div className="hint" style={{ marginTop: 6 }}>{q.metrics.length} points to cover</div>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-ghost btn-sm" onClick={() => setEdit({ ...q })}>Edit</button>
                  <button className="icon-btn" onClick={() => remove(q)} aria-label="Remove"><Icon name="trash" size={15} /></button>
                </div>
              </div>
            ))}
            {!shown.length && <div className="card empty">No questions in this part yet.</div>}
          </div>
        )}
      </div>

      {edit && (
        <>
          <div className="scrim" onClick={() => setEdit(null)} />
          <aside className="drawer" role="dialog" aria-modal="true" aria-label="Edit question">
            <div className="drawer-head">
              <h3>{edit.id ? 'Edit question' : 'New question'}</h3>
              <button className="icon-btn" onClick={() => setEdit(null)} aria-label="Close"><Icon name="x" size={16} /></button>
            </div>
            <div className="drawer-body">
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 110px', gap: 12 }}>
                <div className="field">
                  <label>Part</label>
                  <select className="select" value={edit.part} onChange={set('part')}>
                    {PARTS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                  </select>
                </div>
                <div className="field"><label>Order</label><input className="input" type="number" min={1} value={edit.ordinal} onChange={set('ordinal')} /></div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '110px 1fr', gap: 12 }}>
                <div className="field"><label>Code</label><input className="input" value={edit.code || ''} onChange={set('code')} maxLength={20} placeholder="E51" /></div>
                <div className="field"><label>Category</label><input className="input" value={edit.category || ''} onChange={set('category')} maxLength={120} /></div>
              </div>
              <div className="field"><label>Title</label><input className="input" value={edit.title} onChange={set('title')} maxLength={200} placeholder="e.g. Reply to a delayed migration" /></div>
              <div className="field">
                <label>Scenario <span className="faint">(optional)</span></label>
                <textarea className="textarea" value={edit.scenario || ''} onChange={set('scenario')} placeholder="The situation, or the message the candidate is replying to" />
              </div>
              <div className="field"><label>Task</label><textarea className="textarea" value={edit.task} onChange={set('task')} placeholder="Exactly what the candidate must write" /></div>
              <div className="field">
                <label>Points to cover (marking checklist)</label>
                <span className="hint">What the candidate is expected to write. The AI scores each point out of 10; weights decide how much each counts. Candidates never see these.</span>
                <div style={{ display: 'grid', gap: 8, marginTop: 6 }}>
                  {edit.metrics.map((m, i) => (
                    <div className="metric-edit" key={i}>
                      <input className="input" placeholder="Point to cover" value={m.name} onChange={setMetric(i, 'name')} />
                      <input className="input" type="number" min={1} placeholder="Weight" value={m.weight} onChange={setMetric(i, 'weight')} />
                      <input className="input" placeholder="Note (optional)" value={m.description || ''} onChange={setMetric(i, 'description')} />
                      <button className="icon-btn" onClick={() => setEdit((x) => ({ ...x, metrics: x.metrics.filter((_, j) => j !== i) }))} aria-label="Remove metric"><Icon name="x" size={14} /></button>
                    </div>
                  ))}
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <button className="btn btn-ghost btn-sm" onClick={() => setEdit((x) => ({ ...x, metrics: [...x.metrics, { name: '', weight: 1, description: '' }] }))}><Icon name="plus" size={14} /> Add point</button>
                  {edit.metrics.length > 0 && <span className="hint">Total weight {weightSum}</span>}
                </div>
              </div>
              <label className="switch"><input type="checkbox" checked={edit.active} onChange={set('active')} /> Active (shown in the test)</label>
            </div>
            <div className="drawer-foot">
              <button className="btn btn-ghost" onClick={() => setEdit(null)}>Cancel</button>
              <button className="btn btn-primary" onClick={save} disabled={saving || !edit.title.trim() || !edit.task.trim()}>{saving ? 'Saving…' : 'Save question'}</button>
            </div>
          </aside>
        </>
      )}
    </>
  );
}
