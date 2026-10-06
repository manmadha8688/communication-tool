import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { msal, loginRequest } from '../authConfig';
import api, { errorText } from '../services/api';
import { useToast } from './Toast';

const AuthContext = createContext(null);

/** Where a signed-in person belongs right now. */
export function homeFor(me) {
  if (!me) return '/login';
  if (me.role === 'ADMIN') return '/admin';
  if (!me.profileComplete) return '/profile';
  if (me.testStatus === 'DONE') return '/done';
  if (me.testStatus === 'IN_PROGRESS') return '/test';
  return '/instructions';
}

export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const toast = useToast();
  const [me, setMe] = useState(null);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const exchanging = useRef(false);

  const refresh = useCallback(async () => {
    if (!localStorage.getItem('cfa_token')) {
      setMe(null);
      return null;
    }
    try {
      const { data } = await api.get('/me');
      setMe(data);
      return data;
    } catch {
      setMe(null);
      return null;
    }
  }, []);

  const finish = useCallback(async (data) => {
    localStorage.setItem('cfa_token', data.token);
    setMe(data.me);
    navigate(homeFor(data.me), { replace: true });
  }, [navigate]);

  // Back from Microsoft: main.jsx stored the ID token before the app mounted.
  useEffect(() => {
    (async () => {
      const err = sessionStorage.getItem('cfa_login_error');
      if (err) {
        sessionStorage.removeItem('cfa_login_error');
        toast(err, 'bad');
      }
      const idToken = sessionStorage.getItem('cfa_id_token');
      if (idToken && !exchanging.current) {
        exchanging.current = true;
        sessionStorage.removeItem('cfa_id_token');
        setBusy(true);
        try {
          const { data } = await api.post('/auth/login', { idToken });
          await finish(data);
        } catch (e) {
          toast(errorText(e, 'Sign-in failed. Please try again.'), 'bad');
        } finally {
          setBusy(false);
        }
      } else {
        await refresh();
      }
      setReady(true);
    })();
  }, [finish, refresh, toast]);

  const login = useCallback(async () => {
    setBusy(true);
    try {
      await msal.loginRedirect(loginRequest);
    } catch (e) {
      toast(e?.message || 'Sign-in failed', 'bad');
      setBusy(false);
    }
  }, [toast]);

  const logout = useCallback(async () => {
    localStorage.removeItem('cfa_token');
    sessionStorage.removeItem('cfa_session');
    setMe(null);
    try { await msal.clearCache(); } catch { /* nothing cached */ }
    navigate('/login', { replace: true });
  }, [navigate]);

  const value = useMemo(() => ({ me, setMe, ready, busy, login, logout, refresh }),
    [me, ready, busy, login, logout, refresh]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
