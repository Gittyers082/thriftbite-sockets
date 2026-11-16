-- Admins answer problem reports, and what admins do is written down.

-- A new kind of alert: "we answered your report".
ALTER TABLE alerts DROP CONSTRAINT alerts_kind_check;
ALTER TABLE alerts ADD CONSTRAINT alerts_kind_check
    CHECK (kind IN ('DEAL', 'PICKUP', 'PICKED_UP', 'MISSED', 'REPORT'));

ALTER TABLE reports ADD COLUMN resolved_by UUID REFERENCES users (id) ON DELETE SET NULL;

-- One line per thing an admin did. Lines are only ever added. The names are kept as text,
-- so a line still reads after the admin or the account it was about is gone.
CREATE TABLE admin_actions (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id   UUID         REFERENCES users (id) ON DELETE SET NULL,
    admin_name VARCHAR(50)  NOT NULL,
    action     VARCHAR(30)  NOT NULL,       -- APPROVE_STORE, ANSWER_REPORT
    subject    VARCHAR(160) NOT NULL,       -- who or what it was done to
    note       VARCHAR(500),
    demo       BOOLEAN      NOT NULL DEFAULT false,   -- done by the public demo's admin
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX admin_actions_recent_idx ON admin_actions (created_at DESC);
