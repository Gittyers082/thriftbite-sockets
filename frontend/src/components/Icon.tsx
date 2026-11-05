const paths = {
  bag: <><path d="M4 7h16l-1.5 12.5a1.5 1.5 0 0 1-1.5 1.5H7a1.5 1.5 0 0 1-1.5-1.5z" /><path d="M9 10V6a3 3 0 0 1 6 0v4" /></>,
  cart: <><circle cx="9" cy="20" r="1.4" /><circle cx="18" cy="20" r="1.4" /><path d="M2 3h3l2.6 12.4a2 2 0 0 0 2 1.6h8.6a2 2 0 0 0 2-1.5L22 7H6" /></>,
  ticket: <><path d="M4 8a2 2 0 0 0 2-2h12a2 2 0 0 0 2 2v8a2 2 0 0 0-2 2H6a2 2 0 0 0-2-2z" /><path d="M10 12h4" /></>,
  home: <path d="M4 10l8-6 8 6v9a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z" />,
  plus: <path d="M12 5v14M5 12h14" />,
  store: <><path d="M4 10l1.5-5h13L20 10" /><path d="M4 10h16v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z" /><path d="M9 20v-5h6v5" /></>,
  search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></>,
  back: <path d="M15 18l-6-6 6-6" />,
}

export type IconName = keyof typeof paths

/** Outline icons at 1.6px stroke. Decorative: the label next to them carries the meaning. */
export function Icon({ name, size = 16 }: { name: IconName; size?: number }) {
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
      {paths[name]}
    </svg>
  )
}
