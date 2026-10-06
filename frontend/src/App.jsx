import { Navigate, Route, Routes } from 'react-router-dom';
import { homeFor, useAuth } from './contexts/Auth';
import Login from './pages/Login';
import Profile from './pages/Profile';
import Instructions from './pages/Instructions';
import Exam from './pages/Exam';
import Done from './pages/Done';
import AdminLayout from './pages/admin/AdminLayout';
import Overview from './pages/admin/Overview';
import Candidates from './pages/admin/Candidates';
import CandidateDetail from './pages/admin/CandidateDetail';
import Questions from './pages/admin/Questions';

function Loading() {
  return <div className="center-load"><div className="spinner" /></div>;
}

/** A page only the right person, at the right step, can see. */
function Guard({ role, children }) {
  const { me, ready } = useAuth();
  if (!ready) return <Loading />;
  if (!me) return <Navigate to="/login" replace />;
  if (role && me.role !== role) return <Navigate to={homeFor(me)} replace />;
  return children;
}

export default function App() {
  const { me, ready } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={ready && me ? <Navigate to={homeFor(me)} replace /> : <Login />} />
      <Route path="/profile" element={<Guard role="CANDIDATE"><Profile /></Guard>} />
      <Route path="/instructions" element={<Guard role="CANDIDATE"><Instructions /></Guard>} />
      <Route path="/test" element={<Guard role="CANDIDATE"><Exam /></Guard>} />
      <Route path="/done" element={<Guard role="CANDIDATE"><Done /></Guard>} />
      <Route path="/admin" element={<Guard role="ADMIN"><AdminLayout /></Guard>}>
        <Route index element={<Overview />} />
        <Route path="candidates" element={<Candidates />} />
        <Route path="candidates/:id" element={<CandidateDetail />} />
        <Route path="questions" element={<Questions />} />
      </Route>
      <Route path="*" element={ready ? <Navigate to={homeFor(me)} replace /> : <Loading />} />
    </Routes>
  );
}
