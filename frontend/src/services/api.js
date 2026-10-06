import axios from 'axios';

const api = axios.create({
  // Same origin by default: the web server forwards /api to the backend, in every environment.
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('cfa_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/')) {
      localStorage.removeItem('cfa_token');
      if (window.location.pathname !== '/login') window.location.assign('/login');
    }
    return Promise.reject(error);
  },
);

/** The sentence to show for a failed call. */
export const errorText = (e, fallback = 'Something went wrong. Please try again.') =>
  e?.response?.data?.message || fallback;

export default api;
