import { NavLink, Outlet } from 'react-router-dom';
import Icon from '../../components/Icon';
import { initials } from '../../components/CandidateShell';
import { useAuth } from '../../contexts/Auth';

export default function AdminLayout() {
  const { me, logout } = useAuth();
  return (
    <div className="admin">
      <aside className="side">
        <div className="logo-chip"><img src="/neutara-mark.png" alt="Neutara" /><span className="wordmark">neutara</span></div>
        <div>
          <div className="label">CommuniQ</div>
          <nav>
            <NavLink end to="/admin"><Icon name="grid" /> Overview</NavLink>
            <NavLink to="/admin/candidates"><Icon name="users" /> Candidates</NavLink>
            <NavLink to="/admin/questions"><Icon name="list" /> Questions</NavLink>
          </nav>
        </div>
        <div className="me">
          <div className="avatar">{initials(me?.name || me?.email)}</div>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{me?.name || 'Admin'}</div>
            <div style={{ opacity: .6 }}>Admin</div>
          </div>
          <button onClick={logout} title="Sign out" aria-label="Sign out"><Icon name="logout" size={17} /></button>
        </div>
      </aside>
      <div className="main"><Outlet /></div>
    </div>
  );
}
