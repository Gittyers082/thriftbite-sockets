const paths = {
  browse: <><rect x="4" y="4" width="6.5" height="6.5" /><rect x="13.5" y="4" width="6.5" height="6.5" /><rect x="4" y="13.5" width="6.5" height="6.5" /><rect x="13.5" y="13.5" width="6.5" height="6.5" /></>,
  cart: <><path d="M3 4h3l2 11h11l2-8H7" /><circle cx="9" cy="20" r="1.4" /><circle cx="18" cy="20" r="1.4" /></>,
  ticket: <><path d="M4 5h16v4a3 3 0 0 0 0 6v4H4v-4a3 3 0 0 0 0-6z" /><path d="M14 5v14" strokeDasharray="2 2" /></>,
  person: <><circle cx="12" cy="8" r="4" /><path d="M4 21c1-4 4-6 8-6s7 2 8 6" /></>,
  today: <><rect x="4" y="5" width="16" height="15" /><path d="M4 10h16M9 3v4M15 3v4" /></>,
  sales: <><path d="M4 20h16" /><rect x="5" y="12" width="3" height="8" /><rect x="10.5" y="6" width="3" height="14" /><rect x="16" y="9" width="3" height="11" /></>,
  plus: <><rect x="4" y="4" width="16" height="16" /><path d="M12 8v8M8 12h8" /></>,
  store: <><path d="M4 9l2-5h12l2 5" /><path d="M4 9h16v2a3 3 0 0 1-5 1 3 3 0 0 1-6 0 3 3 0 0 1-5-1z" /><path d="M5 13v7h14v-7M10 20v-4h4v4" /></>,
  back: <path d="M15 18l-6-6 6-6" />,
}

export type IconName = keyof typeof paths

/** Square-capped outline icons. Decorative: the label next to them carries the meaning. */
export function Icon({ name, size = 22 }: { name: IconName; size?: number }) {
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={2.2} strokeLinecap="square">
      {paths[name]}
    </svg>
  )
}
