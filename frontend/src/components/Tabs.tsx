export interface TabItem<T extends string> {
  id: T
  label: string
  count?: number
}

interface TabsProps<T extends string> {
  label: string
  items: TabItem<T>[]
  selected: T
  onSelect: (id: T) => void
}

/** Underline tabs on a full-width rule; the selected tab gets the teal 2px line. */
export function Tabs<T extends string>({ label, items, selected, onSelect }: TabsProps<T>) {
  return (
    <div role="tablist" aria-label={label} className="tabs">
      {items.map((item) => (
        <button key={item.id} type="button" role="tab" className="tab"
          aria-selected={item.id === selected} onClick={() => onSelect(item.id)}>
          {item.label} {item.count !== undefined && <span className="count">{item.count}</span>}
        </button>
      ))}
    </div>
  )
}
