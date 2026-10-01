export type IconName =
  | 'link'
  | 'tag'
  | 'refresh'
  | 'history'
  | 'pencil'
  | 'trash'
  | 'external'
  | 'alert'
  | 'plus'
  | 'check'
  | 'close'
  | 'clock'
  | 'blocked'

const PATHS: Record<IconName, string[]> = {
  link: ['M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1', 'M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1'],
  tag: ['M3.5 12.2V4.5a1 1 0 0 1 1-1h7.7l8.3 8.3a1.5 1.5 0 0 1 0 2.1l-6.4 6.4a1.5 1.5 0 0 1-2.1 0z', 'M8 8h.01'],
  refresh: ['M20 11a8 8 0 0 0-14.6-4.5L4 8', 'M4 4v4h4', 'M4 13a8 8 0 0 0 14.6 4.5L20 16', 'M20 20v-4h-4'],
  history: ['M4 19h16', 'M4 16l4.5-5 3.5 3 4-6 4 4.5'],
  pencil: ['M14.5 5.5l4 4', 'M4 20l1-4.5L15.5 5a2.1 2.1 0 0 1 3 0l.5.5a2.1 2.1 0 0 1 0 3L8.5 19z'],
  trash: ['M4 7h16', 'M9.5 7V4.5h5V7', 'M6.5 7l1 12.5h9l1-12.5', 'M10 11v5', 'M14 11v5'],
  external: ['M14 4h6v6', 'M20 4l-9 9', 'M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5'],
  alert: ['M12 4l9 16H3z', 'M12 10v4', 'M12 17v.5'],
  plus: ['M12 5v14', 'M5 12h14'],
  check: ['M5 12.5l4.5 4.5L19 7.5'],
  close: ['M6 6l12 12', 'M18 6L6 18'],
  clock: ['M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0', 'M12 7.5V12l3 2'],
  blocked: ['M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0', 'M6 6l12 12'],
}

/** One authored icon set: 24px grid, 1.75 stroke, round caps. */
export function Icon({ name, size = 18 }: { name: IconName; size?: number }) {
  return (
    <svg
      className="icon"
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {PATHS[name].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  )
}
