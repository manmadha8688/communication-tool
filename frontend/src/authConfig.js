import { PublicClientApplication } from '@azure/msal-browser';

// Same Microsoft tenant as every CloudFuze tool. This app's origin must be registered as a
// Single-page application redirect URI on the client id below.
export const msal = new PublicClientApplication({
  auth: {
    clientId: import.meta.env.VITE_AZURE_CLIENT_ID || '00000000-0000-0000-0000-000000000000',
    authority: `https://login.microsoftonline.com/${import.meta.env.VITE_AZURE_TENANT_ID || 'common'}`,
    redirectUri: window.location.origin,
    navigateToLoginRequestUrl: false,
  },
  cache: { cacheLocation: 'sessionStorage' },
});

export const loginRequest = { scopes: ['openid', 'profile', 'email'], prompt: 'select_account' };
