import type { ReportKind } from '../api/types'

/** The choices on the "Report a problem" form, in the order they're shown. */
export const reportKinds: { kind: ReportKind; label: string }[] = [
  { kind: 'STORE_CLOSED', label: 'The store was closed' },
  { kind: 'NO_FOOD', label: 'They didn’t have my food' },
  { kind: 'NOT_AS_LISTED', label: 'The food wasn’t what the listing said' },
  { kind: 'UNSAFE_FOOD', label: 'The food looked unsafe to eat' },
  { kind: 'OVERCHARGED', label: 'I was asked to pay more than the app showed' },
  { kind: 'OTHER', label: 'Something else' },
]

export function reportKindLabel(kind: ReportKind): string {
  return reportKinds.find((entry) => entry.kind === kind)?.label ?? 'Something else'
}

/** How long after a reservation a problem with it can still be reported. */
export const REPORT_WITHIN_DAYS = 30
