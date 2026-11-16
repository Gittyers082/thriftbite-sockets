-- An owner can close their store. The row stays (past reservations and reports point at it),
-- but a closed store has no page, no listings and no accounts.
-- Staff accounts need no new column: store_members.role has allowed 'STAFF' since V3.
ALTER TABLE stores ADD COLUMN closed_at TIMESTAMPTZ;
