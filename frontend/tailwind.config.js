/** @type {import('tailwindcss').Config} */
// Design language shared with FusionRM: Inter, 14px base, gray 900 sidebar, indigo primary,
// white cards on a #f9fafb page.
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'] },
      colors: {
        page: '#f9fafb',
        ink: '#111827',
        muted: '#6b7280',
        line: '#e5e7eb',
        primary: { DEFAULT: '#4f46e5', hover: '#4338ca', ring: '#6366f1', soft: '#eef2ff' },
        sidebar: { DEFAULT: '#111827', active: '#1f2937', text: '#9ca3af', tag: '#a5b4fc' },
        code: '#0b1220',
      },
      boxShadow: {
        card: '0 0 0 1px rgba(17,24,39,0.05), 0 1px 2px rgba(0,0,0,0.05)',
      },
    },
  },
  plugins: [],
}
