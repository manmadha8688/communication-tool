/** A small, consistent stroke icon set (24px grid), so the app needs no icon library. */
const PATHS = {
  chat: 'M4 5h16v11H8l-4 4z',
  mail: 'M3 6h18v12H3z M3 7l9 6 9-6',
  meeting: 'M3 7h12v10H3z M15 10l6-3v10l-6-3',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 7v5l3 2',
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z',
  check: 'M5 12l4.5 4.5L19 7',
  x: 'M6 6l12 12 M18 6L6 18',
  alert: 'M12 3l10 18H2z M12 10v5 M12 18v.5',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  screen: 'M3 4h18v12H3z M8 20h8 M12 16v4',
  copy: 'M8 8h12v12H8z M16 8V4H4v12h4',
  lock: 'M5 11h14v10H5z M8 11V7a4 4 0 0 1 8 0v4',
  users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M2 21c0-4 3-6 7-6s7 2 7 6 M17 11a3 3 0 0 0 0-6 M22 21c0-3-2-5-5-5',
  grid: 'M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z',
  list: 'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
  download: 'M12 3v12 M7 10l5 5 5-5 M4 21h16',
  plus: 'M12 5v14 M5 12h14',
  trash: 'M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13',
  arrow: 'M5 12h14 M13 6l6 6-6 6',
  back: 'M19 12H5 M11 6l-6 6 6 6',
  logout: 'M15 4h4v16h-4 M10 8l-4 4 4 4 M6 12h10',
  refresh: 'M20 11a8 8 0 1 0-2 6 M20 5v6h-6',
  flag: 'M5 21V4 M5 4h12l-2 4 2 4H5',
};

export default function Icon({ name, size = 18, stroke = 1.8, ...rest }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...rest}>
      {PATHS[name].split(' M').map((d, i) => <path key={i} d={i ? `M${d}` : d} />)}
    </svg>
  );
}
