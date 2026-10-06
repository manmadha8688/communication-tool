import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { msal } from './authConfig';
import { ToastProvider } from './contexts/Toast';
import { AuthProvider } from './contexts/Auth';
import App from './App';
import './styles.css';

// Finish the Microsoft redirect before React mounts, and hand the ID token to AuthProvider.
async function boot() {
  try {
    await msal.initialize();
    const result = await msal.handleRedirectPromise();
    if (result?.idToken) sessionStorage.setItem('cfa_id_token', result.idToken);
  } catch (e) {
    sessionStorage.setItem('cfa_login_error', e?.message || 'Microsoft sign-in failed.');
  }
  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <BrowserRouter>
        <ToastProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </ToastProvider>
      </BrowserRouter>
    </React.StrictMode>,
  );
}

boot();
