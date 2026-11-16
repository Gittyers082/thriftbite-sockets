-- An admin can suspend a buyer or a store, with a reason the account holder is shown.
ALTER TABLE users ADD COLUMN suspended_at     TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN suspended_reason VARCHAR(300);

-- A new kind of alert: a pickup cancelled because its store was suspended.
ALTER TABLE alerts DROP CONSTRAINT alerts_kind_check;
ALTER TABLE alerts ADD CONSTRAINT alerts_kind_check
    CHECK (kind IN ('DEAL', 'PICKUP', 'PICKED_UP', 'MISSED', 'REPORT', 'CANCELLED'));
