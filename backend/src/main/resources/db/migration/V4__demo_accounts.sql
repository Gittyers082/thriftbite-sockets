-- Marks the accounts the public demo creates, so the demo admin can be kept
-- to the demo's own sample data and never sees or approves real sign-ups.
-- Existing rows are real accounts; the demo reseeds (and marks) its own on startup.
ALTER TABLE users ADD COLUMN demo boolean NOT NULL DEFAULT false;
