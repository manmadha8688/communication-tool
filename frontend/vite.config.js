import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // `npm run dev` forwards /api to a locally running backend, the same way nginx does in Docker.
  server: { port: 5185, host: true, proxy: { '/api': 'http://localhost:8095' } },
  build: {
    rollupOptions: {
      output: {
        // Sign-in and React change rarely; keep them in their own long-cached files.
        manualChunks(id) {
          if (id.includes('@azure/msal')) return 'msal';
          if (/node_modules\/(react|react-dom|react-router|react-router-dom|scheduler|@remix-run)\//.test(id)) return 'react';
          return undefined;
        },
      },
    },
  },
});
